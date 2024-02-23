package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DownloadRuleTests {

    private DownloadRule rule;

    private VacoProperties vacoProperties;

    @Mock
    private TaskService taskService;

    @Mock
    private VacoHttpClient httpClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private FindingService findingService;

    @Captor
    private ArgumentCaptor<Path> tempFilePath;
    @Captor
    private ArgumentCaptor<S3Path> targetPath;
    @Captor
    private ArgumentCaptor<Path> sourcePath;
    private Path gtfsTestFile;

    @BeforeEach
    void setUp() throws URISyntaxException {
        gtfsTestFile = Path.of(Thread.currentThread().getContextClassLoader().getResource("public/testfiles/padasjoen_kunta.zip").toURI());
        vacoProperties = TestObjects.vacoProperties();
        rule = new DownloadRule(taskService, vacoProperties, httpClient, s3Client, findingService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskService, httpClient, s3Client, findingService);
    }

    @Test
    void ruleExecution() {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task dlTask = ImmutableTask.of(-1L, DownloadRule.DOWNLOAD_SUBTASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();

        given(taskService.findTask(entry.publicId(), DownloadRule.DOWNLOAD_SUBTASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(httpClient.downloadFile(tempFilePath.capture(), eq(entry.url()), eq(entry.etag()))).willAnswer(a -> CompletableFuture.completedFuture(Optional.ofNullable(gtfsTestFile)));
        given(taskService.trackTask(entry, dlTask, ProcessingState.UPDATE)).willReturn(dlTask);
        given(s3Client.uploadFile(eq(vacoProperties.s3ProcessingBucket()), targetPath.capture(), sourcePath.capture())).willReturn(CompletableFuture.completedFuture(null));
        given(taskService.trackTask(entry, dlTask, ProcessingState.COMPLETE)).willReturn(dlTask);
        given(taskService.markStatus(entry, dlTask, Status.SUCCESS)).willReturn(dlTask);

        ResultMessage result = rule.execute(entry).join();

        assertThat(result.ruleName(), equalTo(DownloadRule.DOWNLOAD_SUBTASK));

        assertThat(tempFilePath.getValue().toString(), endsWith("entries/" + entry.publicId() + "/tasks/prepare.download/gtfs.zip"));
        assertThat(targetPath.getValue().toString(), equalTo("entries/" + entry.publicId() + "/tasks/prepare.download/rules/prepare.download/output/gtfs.zip"));
    }
}
