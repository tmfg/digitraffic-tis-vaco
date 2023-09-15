package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class S3ClientTests extends AwsIntegrationTestBase {

    private S3Client s3Client;

    private static VacoProperties vacoProperties;

    @TempDir
    static Path testDirectory;
    static Path inputs;
    static Path outputs;

    @BeforeAll
    static void beforeAll() throws IOException {
        inputs = testDirectory.resolve("inputs");
        Files.createDirectories(inputs);

        outputs = testDirectory.resolve("outputs");
        Files.createDirectories(outputs);

        vacoProperties = TestObjects.vacoProperties();
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.getS3ProcessingBucket()).build());
    }

    @BeforeEach
    void setUp() {
        s3Client = new S3Client(vacoProperties, s3TransferManager, awsS3Client);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void canRoundtripASingleFile() throws IOException {
        String content = "Hail, traveller!";
        Path inputContent = writeContent(inputs.resolve("hello.txt"), content);
        Path outputContent = outputs.resolve("hello.txt");

        s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), ImmutableS3Path.of("hello.txt"), inputContent).join();
        Long contentLength = s3Client.downloadFile(vacoProperties.getS3ProcessingBucket(), "hello.txt", outputContent);

        assertThat(contentLength.intValue(), equalTo(content.length()));
        assertThat(Files.readString(outputContent), equalTo(content));
    }

    @Test
    void canRoundtripEntireDirectory() throws IOException {
        Path inputManyFiles = Files.createDirectories(outputs.resolve("manyFiles"));
        writeContent(inputManyFiles.resolve("a.txt"), "a");
        writeContent(inputManyFiles.resolve("b.txt"), "b");
        writeContent(inputManyFiles.resolve("c.txt"), "c");
        Path outputManyFiles = Files.createDirectories(outputs.resolve("manyFiles"));

        s3Client.uploadDirectory(inputManyFiles, vacoProperties.getS3ProcessingBucket(), ImmutableS3Path.of("bunchOfFiles")).join();
        s3Client.downloadDirectory(vacoProperties.getS3ProcessingBucket(), ImmutableS3Path.of("bunchOfFiles"), outputManyFiles).join();

        assertThat(Files.readString(outputManyFiles.resolve("a.txt")), equalTo("a"));
        assertThat(Files.readString(outputManyFiles.resolve("b.txt")), equalTo("b"));
        assertThat(Files.readString(outputManyFiles.resolve("c.txt")), equalTo("c"));
    }

    @NotNull
    private static Path writeContent(Path path, String content) throws IOException {
        Path sourcePath = testDirectory.resolve(path);
        return Files.writeString(Files.createFile(sourcePath), content);
    }
}
