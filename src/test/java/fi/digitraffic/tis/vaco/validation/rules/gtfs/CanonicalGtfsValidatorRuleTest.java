package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
class CanonicalGtfsValidatorRuleTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
            .withServices(Service.S3)
            .withCommand();

    private static S3AsyncClient s3;
    private static S3TransferManager s3TransferManager;
    private static String testBucket = "vaco-test-canonical-gtfs-validator";
    private static VacoProperties vacoProperties;

    private CanonicalGtfsValidatorRule rule;
    private ImmutableQueueEntry queueEntry;

    @BeforeAll
    static void beforeAll() {
        vacoProperties = new VacoProperties("test", null, testBucket);
        s3 = S3AsyncClient.crtBuilder()
                .endpointOverride(localstack.getEndpointOverride(Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket(testBucket).build()).join();
        s3TransferManager = S3TransferManager.builder().s3Client(s3).build();
    }

    @BeforeEach
    void setUp() {
        rule = new CanonicalGtfsValidatorRule(s3TransferManager, vacoProperties);
        queueEntry = ImmutableQueueEntry.builder()
                .format("gtfs")
                .publicId("testPublicId")
                .url("http://nonexistent.url")
                .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
                .build();
    }

    @Test
    void validatesGivenEntry() throws URISyntaxException, IOException {
        ValidationReport report = rule.execute(queueEntry, forInput("public/testfiles/padasjoen_kunta.zip")).join();

        assertThat(report.errors(), empty());

        ListObjectsV2Response r = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(testBucket).build()).join();
        r.contents().forEach(s3Object -> System.out.println("s3Object.key() = " + s3Object.key()));

        Path f = downloadFileFromS3("report.json");
        System.out.println("f = " + Files.readString(f));
    }

    private Path downloadFileFromS3(String file) {
        //              entries/testPublicId/phases/validation.execute/gtfs.canonical.v4_0_0/report.json
        String s3Key = "entries/testPublicId/phases/"
                + ValidationService.EXECUTION_PHASE
                + "/"
                + CanonicalGtfsValidatorRule.RULE_NAME
                + "/output/"
                + file;
        Path output = Paths.get(vacoProperties.getTemporaryDirectory(), file);
        System.out.println("output = " + output);
        DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
                .getObjectRequest(req -> req.bucket(testBucket).key(s3Key))
                .destination(output)
                .addTransferListener(LoggingTransferListener.create())
                .build();

        FileDownload download = s3TransferManager.downloadFile(downloadFileRequest);

        // Wait for the transfer to complete
        download.completionFuture().join();

        return output;
    }

    @Test
    void wontAcceptNonGtfsFormatEntries() throws URISyntaxException {
        QueueEntry invalidFormat = ImmutableQueueEntry.copyOf(queueEntry).withFormat("vhs");
        ValidationReport report = rule.execute(invalidFormat, forInput("public/testfiles/padasjoen_kunta.zip")).join();

        assertThat(report.errors(), equalTo(List.of(ImmutableError.of("Wrong format! Expected 'gtfs', was 'vhs'"))));

    }

    @NotNull
    private ImmutablePhaseData<FileReferences> forInput(String testFile) throws URISyntaxException {
        return ImmutablePhaseData.<FileReferences>builder()
                .phase(ImmutablePhase.builder().name(ValidationService.EXECUTION_PHASE).build())
                .payload(ImmutableFileReferences.builder()
                        .localPath(testResource(testFile))
                        .build())
                .build();
    }

    private Path testResource(String resourceName) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource).toURI());
    }
}
