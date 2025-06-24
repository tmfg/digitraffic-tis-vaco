package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.aws.S3Packager;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.PackageRecord;
import fi.digitraffic.tis.vaco.db.repositories.PackageRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Service
public class PackagesService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RecordMapper recordMapper;

    private final VacoProperties vacoProperties;

    private final S3Client s3Client;

    private final S3Packager s3Packager;

    private final CachingService cachingService;

    private final PackageRepository packageRepository;

    private final TaskRepository taskRepository;

    public PackagesService(VacoProperties vacoProperties,
                           S3Client s3Client,
                           S3Packager s3Packager,
                           CachingService cachingService,
                           PackageRepository packageRepository,
                           TaskRepository taskRepository,
                           RecordMapper recordMapper) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.s3Packager = Objects.requireNonNull(s3Packager);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.packageRepository = Objects.requireNonNull(packageRepository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    /**
     * Produces a packages from given S3 coordinates relating to specific entry+task combination.
     *
     * @param entry Entry this package relates to.
     * @param task Task from which this package originates from.
     * @param packageName Package name to produce. This should be unique per task.
     * @param packageContentsS3Path Where in S3 the package contents are stored before packaging.
     * @param fileName Final file name for the package.
     * @param contentFilter Filter predicate for matching each file name for inclusion. Allows fine-grained control over
     *                      package contents.
     * @return Created package
     * @see S3Packager#producePackage(Entry, S3Path, S3Path, String, Predicate)
     */

    public Package createPackage(Entry entry,
                                 Task task,
                                 String packageName,
                                 S3Path packageContentsS3Path,
                                 String fileName,
                                 Predicate<String> contentFilter) {
        // upload package file to S3 long term storage
        s3Packager.producePackage(
                        entry,
                        S3Artifact.getRuleDirectory(entry.publicId(), task.name(), task.name()),
                        packageContentsS3Path,
                        fileName,
                        contentFilter)
            .join();

        // store database reference
        return registerPackage(ImmutablePackage.of(
                task,
                packageName,
                ImmutableS3Path.builder()
                    .from(packageContentsS3Path)
                    .addPath(fileName)
                    .build()
                    .toString()));
    }

    /**
     * Creates new package using provided {@link Package} information as is. Useful for generating ZIPs in rules and
     * publishing them directly.
     *
     * @param p Package to save.
     * @return Saved Package with updated ids, references etc.
     */
    public Package registerPackage(Package p) {
        return recordMapper.toPackage(p.task(), packageRepository.upsertPackage(p));
    }

    public List<Package> findAvailablePackages(Task task) {
        List<PackageRecord> packages = packageRepository.findPackages(task);
        return Streams.filter(packages, p -> s3Client.keyExists(vacoProperties.s3PackagesBucket(), p.path()))
            .map(p -> recordMapper.toPackage(task, p))
            .toList();
    }

    public Optional<Package> findPackage(Task task, String packageName) {
        return taskRepository.findByPublicId(task.publicId())
            .flatMap(t -> packageRepository.findPackage(t, packageName))
            .map(packageRecord -> recordMapper.toPackage(task, packageRecord));
    }

    public Optional<Path> downloadPackage(Entry entry, Task task, String packageName) {
        return findPackage(task, packageName)
            .flatMap(p -> {
                Path targetPackagePath = TempFiles.getPackageDirectory(vacoProperties, entry, task, packageName)
                    .resolve(Path.of(p.path()).getFileName());

                return cachingService.cacheLocalTemporaryPath(targetPackagePath, path -> {
                    logger.info("Downloading s3://{}/{} to local temp path {}", vacoProperties.s3PackagesBucket(), p.path(), targetPackagePath);

                    s3Client.downloadFile(
                        vacoProperties.s3PackagesBucket(),
                        S3Path.of(p.path()),  // reuse local path as S3 path key
                        targetPackagePath);
                    return targetPackagePath;
                });
            });
    }
}
