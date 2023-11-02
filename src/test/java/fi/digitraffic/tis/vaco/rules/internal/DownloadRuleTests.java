package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

class DownloadRuleTests extends SpringBootIntegrationTestBase {

    @Autowired
    private DownloadRule rule;

    @Autowired
    private VacoProperties vacoProperties;

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @MockBean
    private HttpClient httpClient;

    @Captor
    private ArgumentCaptor<Path> filePath;

    @Captor
    private ArgumentCaptor<String> entryUrl;

    @Captor
    private ArgumentCaptor<String> entryEtag;

    @Mock
    HttpResponse<Path> response;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
    }

    @Test
    void ruleExecution() throws IOException, URISyntaxException {
        when(httpClient.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        Entry entry = createEntryForTesting();

        S3Path result = rule.execute(entry).join();

        assertThat(entryUrl.getValue(), equalTo("https://testfile"));

        List<S3Object> uploadedFiles = awsS3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(vacoProperties.s3ProcessingBucket())
                .prefix("entries/" + entry.publicId())
                .build())
            .contents();

        assertThat(uploadedFiles.size(), equalTo(1));

        String key = uploadedFiles.get(0).key();
        Path redownload = Paths.get(vacoProperties.temporaryDirectory(), "testfile");
        s3TransferManager.downloadFile(DownloadFileRequest.builder()
                .getObjectRequest(req -> req.bucket(vacoProperties.s3ProcessingBucket()).key(key))
                .destination(redownload)
                .build())
            .completionFuture()
            .join();
        assertThat(Files.readString(redownload), equalTo("I'm smol :3\n"));

        Files.delete(redownload);
    }

    private Entry createEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").build());
    }
}
