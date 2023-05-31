package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

class ValidationServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private VacoProperties vacoProperties;

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3TransferManager s3TransferManager;

    @MockBean
    private HttpClient httpClient;

    @Captor
    private ArgumentCaptor<HttpRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<HttpResponse.BodyHandler<Path>> bodyHandlerCaptor;

    @Mock
    HttpResponse<Path> response;

    @Test
    void uploadsDownloadedFileToS3() throws URISyntaxException, IOException {
        when(httpClient.sendAsync(requestCaptor.capture(), bodyHandlerCaptor.capture())).thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        ImmutableQueueEntry entry = createQueueEntryForTesting();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.getS3processingBucket()).build());

        validationService.validate(ImmutableJobDescription.builder()
                .message(entry)
                .build());

        assertThat(requestCaptor.getValue().uri(), equalTo(new URI("https://testfile")));

        List<S3Object> uploadedFiles = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(vacoProperties.getS3processingBucket())
                        .prefix("entries/" + entry.publicId())
                        .build())
                .contents();

        assertThat(uploadedFiles.size(), equalTo(1));

        String key = uploadedFiles.get(0).key();
        Path redownload = Paths.get(vacoProperties.getTemporaryDirectory(), "testfile");
        s3TransferManager.downloadFile(DownloadFileRequest.builder()
                        .getObjectRequest(req -> req.bucket(vacoProperties.getS3processingBucket()).key(key))
                        .destination(redownload)
                        .build())
                .completionFuture()
                .join();
        assertThat(Files.readString(redownload), equalTo("I'm smol :3\n"));

        Files.delete(redownload);
    }

    private ImmutableQueueEntry createQueueEntryForTesting() {
        return queueHandlerRepository.create(
                ImmutableQueueEntry.builder()
                        .format("huuhaa")
                        .url("https://testfile")
                        .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
                        .build());
    }
}
