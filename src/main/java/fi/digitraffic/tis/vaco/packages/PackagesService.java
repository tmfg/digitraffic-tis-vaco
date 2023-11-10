package fi.digitraffic.tis.vaco.packages;

import com.github.benmanes.caffeine.cache.Cache;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.aws.S3Packager;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Service
public class PackagesService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VacoProperties vacoProperties;
    private final S3Client s3Client;
    private final S3Packager s3Packager;
    private final PackagesRepository packagesRepository;
    private final Cache<Path, Path> packageCache;

    public PackagesService(VacoProperties vacoProperties,
                           S3Client s3Client,
                           S3Packager s3Packager,
                           PackagesRepository packagesRepository,
                           @Qualifier("packagesCache") Cache<Path, Path> packageCache) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.s3Packager = Objects.requireNonNull(s3Packager);
        this.packagesRepository = Objects.requireNonNull(packagesRepository);
        this.packageCache = Objects.requireNonNull(packageCache);
    }

    /**
     * Produces a packages from given S3 coordinates relating to specific entry+task combination.
     *
     * @param entry Entry this package relates to.
     * @param task Task from which this package originates from.
     * @param packageName Package name to produce. This should be unique per task.
     * @param packageContentsS3Path Where in S3 the package contents are stored before packaging.
     * @param fileName Final file name for the package.
     * @param contentFilter Filter predicate for matching each file name for inclusion. Allows finegrained control over
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
        // upload package file to S3
        s3Packager.producePackage(
                        entry,
                        S3Artifact.getRuleDirectory(entry.publicId(), task.name(), task.name()),
                        packageContentsS3Path,
                        fileName,
                        contentFilter)
            .join();

        // store database reference
        return registerPackage(ImmutablePackage.of(
                task.id(),
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
        return packagesRepository.createPackage(p);
    }

    public List<Package> findPackages(Task task) {
        return packagesRepository.findPackages(task);
    }

    public Optional<Package> findPackage(Task task, String packageName) {
        return packagesRepository.findPackage(task, packageName);
    }

    public Optional<Path> downloadPackage(Entry entry, Task task, String packageName) {
        return findPackage(task, packageName).map(p -> {
            Path targetPackagePath = TempFiles.getPackageDirectory(vacoProperties, entry, task, packageName)
                .resolve(Path.of(p.path()).getFileName());

            return packageCache.get(targetPackagePath, tpp -> {
                logger.info("Downloading s3://{}/{} to local temp path {}", vacoProperties.s3ProcessingBucket(), p.path(), targetPackagePath);

                s3Client.downloadFile(
                    vacoProperties.s3ProcessingBucket(),
                    S3Path.of(p.path()),  // reuse local path as S3 path key
                    targetPackagePath);
                return targetPackagePath;
            });
        });
    }
}
