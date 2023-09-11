package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.aws.S3Packager;
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

@Service
public class PackagesService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VacoProperties vacoProperties;
    private final S3Client s3Client;
    private final S3Packager s3Packager;
    private final PackagesRepository packagesRepository;

    public PackagesService(VacoProperties vacoProperties,
                           S3Client s3Client,
                           S3Packager s3Packager,
                           PackagesRepository packagesRepository) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.s3Packager = Objects.requireNonNull(s3Packager);
        this.packagesRepository = Objects.requireNonNull(packagesRepository);
    }

    public Optional<Path> findPackage(String entryPublicId, String packageName) {
        Optional<Package> fp = packagesRepository.findPackage(entryPublicId, packageName);

        return fp.map(p -> {
            Path targetPackagePath = TempFiles.getPackageDirectory(vacoProperties, entryPublicId, packageName)
                .resolve(Path.of(p.path()).getFileName());

            logger.info("Downloading s3:{}:{} to local temp path {}", vacoProperties.getS3ProcessingBucket(), p.path(), targetPackagePath);

            s3Client.downloadFile(
                vacoProperties.getS3ProcessingBucket(),
                p.path(),
                targetPackagePath);
            return targetPackagePath;
        });
    }

    public ImmutablePackage createPackage(Entry entry, Task task, String ruleName, String fileName) {
        // TODO: error handling?
        // upload package file to S3
        String packagePath = S3Artifact.getPackagePath(entry.publicId(), ruleName);
        s3Packager.producePackage(
                entry,
                S3Artifact.getRuleDirectory(entry.publicId(), task.name(), ruleName),
                packagePath,
                fileName)
            .join();

        return packagesRepository.createPackage(
            ImmutablePackage.of(
                entry.id(),
                ruleName,  // TODO: This assumes same rule can be executed only once per job
                packagePath + "/" + fileName));
    }

    public List<ImmutablePackage> findPackages(Long entryId) {
        return packagesRepository.findPackages(entryId);
    }
}
