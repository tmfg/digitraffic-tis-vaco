package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static fi.digitraffic.tis.vaco.aws.TestData.allFiles;
import static fi.digitraffic.tis.vaco.aws.TestData.directoryToIgnore;
import static fi.digitraffic.tis.vaco.aws.TestData.fileInIgnoredDirectoryName;
import static fi.digitraffic.tis.vaco.aws.TestData.fileInSubDirectoryName;
import static fi.digitraffic.tis.vaco.aws.TestData.fileToIgnoreInSubDirectoryName;
import static fi.digitraffic.tis.vaco.aws.TestData.inputRootDirectoryPath;
import static fi.digitraffic.tis.vaco.aws.TestData.outputDirectoryPath;
import static fi.digitraffic.tis.vaco.aws.TestData.someFileName;
import static fi.digitraffic.tis.vaco.aws.TestData.subDirectory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class S3PackagerIntegrationTests extends SpringBootIntegrationTestBase {

    private Entry entry;

    @TempDir
    static File testDirectory;

    @Autowired
    private S3Packager s3Packager;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private VacoProperties vacoProperties;

    @BeforeAll
    static void prepareTestDirectory() throws IOException {
        // Prepare sub-directories:
        inputRootDirectoryPath = Files.createDirectories(testDirectory.toPath()
            .resolve(TestData.inputRootDirectory));
        Path subDirectoryPath = Files.createDirectories(testDirectory.toPath()
            .resolve(TestData.inputRootDirectory)
            .resolve(subDirectory));
        Path directoryToIgnorePath = Files.createDirectories(testDirectory.toPath()
            .resolve(TestData.inputRootDirectory)
            .resolve(TestData.directoryToIgnore));
        TestData.outputDirectoryPath = Files.createDirectories(testDirectory.toPath()
            .resolve(TestData.outputDirectory));
        // Prepare files:
        createTestFile(inputRootDirectoryPath, TestData.someFileName, false);
        createTestFile(subDirectoryPath, fileInSubDirectoryName, true);
        createTestFile(directoryToIgnorePath, TestData.fileInIgnoredDirectoryName, true);
        createTestFile(subDirectoryPath, TestData.fileToIgnoreInSubDirectoryName, true);
    }

    static void createTestFile(Path directory, String fileName, boolean includeParentDir) throws IOException {
        File file = new File(directory.toFile(), fileName);
        file.createNewFile();

        allFiles.add((includeParentDir ? file.getParentFile().getName() + "/" : "") + fileName);
    }

    @BeforeEach
    void prepareBucketWithData() {
        entry = TestObjects.anEntry("gtfs").build();

        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
        s3Client.uploadDirectory(testDirectory.toPath(), vacoProperties.s3ProcessingBucket(), ImmutableS3Path.of("")).join();
    }

    @AfterEach
    void s3Cleanup() {
        List<ObjectIdentifier> objects = Streams.map(s3Client.listObjectsInBucket(ImmutableS3Path.of(""), vacoProperties.s3ProcessingBucket()),
            obj -> ObjectIdentifier.builder().key(obj.key()).build()).toList();
        awsS3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).delete(Delete.builder().objects(objects).build()).build());
        awsS3Client.deleteBucket(DeleteBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
    }

    ZipFile downloadProducedZipPackage(String packageName) throws IOException {
        byte[] producePackageData = s3Client.getObjectBytes(TestData.outputDirectory + "/" + packageName);
        File zipFile = new File(TestData.outputDirectoryPath.toFile(), UUID.randomUUID() + ".zip");
        zipFile.createNewFile();
        try(OutputStream os = new FileOutputStream(zipFile)) {
            os.write(producePackageData);
        }
        return new ZipFile(zipFile);
    }

    void assertZipFileContents(List<String> expectedFiles, ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        List<String> filesInZip = Streams.filter(entries, Predicate.not(ZipEntry::isDirectory))
            .map(ZipEntry::getName)
            .toList();

        // Set wraps because we do not care about exact order
        assertThat(new HashSet<>(filesInZip), equalTo(new HashSet<>(expectedFiles)));
    }

    @Test
    void testPackageAllFiles() throws ExecutionException, InterruptedException, IOException {
       String packageFileName = UUID.randomUUID().toString();
       s3Packager.producePackage(entry, TestData.s3InputRootDirectory, TestData.s3OutputDirectory, packageFileName, p -> true)
           .get();

       ZipFile producedPackageZip = downloadProducedZipPackage(packageFileName);
       assertZipFileContents(allFiles, producedPackageZip);
       producedPackageZip.close();
    }

    @Test
    void testPackageWithFilteredDirectory() throws IOException, ExecutionException, InterruptedException {
        String packageFileName = UUID.randomUUID().toString();
        s3Packager.producePackage(entry, TestData.s3InputRootDirectory, TestData.s3OutputDirectory,
                packageFileName, p -> !p.matches(".*" + directoryToIgnore + ".*")).get();

        ZipFile producedPackageZip = downloadProducedZipPackage(packageFileName);
        List<String> filteredFiles = new ArrayList<>(List.copyOf(allFiles));
        filteredFiles.remove(directoryToIgnore + "/" + fileInIgnoredDirectoryName);
        assertZipFileContents(filteredFiles, producedPackageZip);
        producedPackageZip.close();
    }

    @Test
    void testPackageWithFilteredFile() throws IOException, ExecutionException, InterruptedException {
        String packageFileName = UUID.randomUUID().toString();
        s3Packager.producePackage(entry, TestData.s3InputRootDirectory, TestData.s3OutputDirectory,
            packageFileName, p -> !p.matches(".*" + fileToIgnoreInSubDirectoryName)).get();

        ZipFile producedPackageZip = downloadProducedZipPackage(packageFileName);
        List<String> filteredFiles = new ArrayList<>(List.copyOf(allFiles));
        filteredFiles.remove(subDirectory + "/" +fileToIgnoreInSubDirectoryName);
        assertZipFileContents(filteredFiles, producedPackageZip);
        producedPackageZip.close();
    }

    @Test
    void testPackageWithMultipleFilters() throws IOException, ExecutionException, InterruptedException {
        String packageFileName = UUID.randomUUID().toString();
        s3Packager.producePackage(entry, TestData.s3InputRootDirectory, TestData.s3OutputDirectory,
            packageFileName,
            p -> !p.matches(".*" + fileToIgnoreInSubDirectoryName) && !p.matches(".*" + directoryToIgnore + ".*"))
                .get();

        ZipFile producedPackageZip = downloadProducedZipPackage(packageFileName);
        List<String> filteredFiles = new ArrayList<>(List.copyOf(allFiles));
        filteredFiles.remove(subDirectory + "/" + fileToIgnoreInSubDirectoryName);
        filteredFiles.remove(directoryToIgnore + "/" + fileInIgnoredDirectoryName);
        assertZipFileContents(filteredFiles, producedPackageZip);
        producedPackageZip.close();
    }

    @Test
    void testPackageWithFilteredFilesByExtension() throws IOException, ExecutionException, InterruptedException {
        String packageFileName = UUID.randomUUID().toString();
        s3Packager.producePackage(entry, TestData.s3InputRootDirectory, TestData.s3OutputDirectory,
                packageFileName,
                p -> !p.endsWith(".java") // all Java files
                        && !p.matches(".*/subDirectory/.*\\.py") // all Python files under subDirectory
        ).get();

        ZipFile producedPackageZip = downloadProducedZipPackage(packageFileName);
        List<String> filteredFiles = new ArrayList<>(List.copyOf(allFiles));
        filteredFiles.remove(someFileName);
        filteredFiles.remove(subDirectory + "/" + fileToIgnoreInSubDirectoryName);
        assertZipFileContents(filteredFiles, producedPackageZip);
        producedPackageZip.close();
    }

    @Test
    void testDirectoryCleanup() throws IOException {
        assertThat(Objects.requireNonNull(inputRootDirectoryPath.toFile().listFiles()).length, equalTo(3));
        s3Packager.cleanup(inputRootDirectoryPath.toFile());
        assertThat(inputRootDirectoryPath.toFile().listFiles(), nullValue());
        assertThat(outputDirectoryPath.toFile().listFiles(), notNullValue());
    }
}

class TestData {
    static String inputRootDirectory = "inputRootDirectory";
    static S3Path s3InputRootDirectory = ImmutableS3Path.of(inputRootDirectory);
    static Path inputRootDirectoryPath;
    static String subDirectory = "subDirectory";
    static String directoryToIgnore = "directoryToIgnore";
    static String someFileName = "someFile.java";
    static String fileInSubDirectoryName = "fileInSubDirectory.txt";
    static String fileInIgnoredDirectoryName = "fileInIgnoredDirectory.txt";
    static String fileToIgnoreInSubDirectoryName = "fileToIgnoreInSubDirectory.py";
    static String outputDirectory = "outputDirectory";
    static S3Path s3OutputDirectory = ImmutableS3Path.of(outputDirectory);
    static Path outputDirectoryPath;
    static List<String> allFiles = new ArrayList<>();
}
