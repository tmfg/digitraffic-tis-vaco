package fi.digitraffic.tis.vaco.rules.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
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
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DownloadRuleTests {

    private DownloadRule rule;

    private VacoProperties vacoProperties;

    private ObjectMapper objectMapper;

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

    @BeforeEach
    void setUp() throws URISyntaxException {
        objectMapper = new ObjectMapper();
        objectMapper.registerModules(new GuavaModule());
        vacoProperties = TestObjects.vacoProperties();
        rule = new DownloadRule(objectMapper, taskService, vacoProperties, httpClient, s3Client, findingService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskService, httpClient, s3Client, findingService);
    }

    @Test
    void ruleExecutionForGtfs() throws URISyntaxException {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry(TransitDataFormat.GTFS.fieldName());
        Task dlTask = ImmutableTask.of(-1L, DownloadRule.PREPARE_DOWNLOAD_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();
        Path gtfsTestFile = resolveTestFile("padasjoen_kunta.zip");

        given(taskService.findTask(entry.publicId(), DownloadRule.PREPARE_DOWNLOAD_TASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(httpClient.downloadFile(tempFilePath.capture(), eq(entry.url()), eq(entry.etag()))).willAnswer(a -> CompletableFuture.completedFuture(Optional.ofNullable(gtfsTestFile)));
        given(taskService.trackTask(entry, dlTask, ProcessingState.UPDATE)).willReturn(dlTask);
        given(s3Client.uploadFile(eq(vacoProperties.s3ProcessingBucket()), targetPath.capture(), sourcePath.capture())).willReturn(CompletableFuture.completedFuture(null));
        given(taskService.trackTask(entry, dlTask, ProcessingState.COMPLETE)).willReturn(dlTask);
        given(taskService.markStatus(entry, dlTask, Status.SUCCESS)).willReturn(dlTask);

        ResultMessage result = rule.execute(entry).join();

        assertThat(result.ruleName(), equalTo(DownloadRule.PREPARE_DOWNLOAD_TASK));

        assertThat(tempFilePath.getValue().toString(), endsWith("entries/" + entry.publicId() + "/tasks/prepare.download/gtfs.zip"));
        assertThat(targetPath.getValue().toString(), equalTo("entries/" + entry.publicId() + "/tasks/prepare.download/rules/prepare.download/output/gtfs.zip"));
    }

    @Captor
    private ArgumentCaptor<Path> feedFiles;

    @Test
    void ruleExecutionForGbfs() throws URISyntaxException {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry(TransitDataFormat.GBFS.fieldName());
        Task dlTask = ImmutableTask.of(-1L, DownloadRule.PREPARE_DOWNLOAD_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();
        Path gbfsDiscoveryFile = resolveTestFile("lahti_gbfs/gbfs.json");

        given(taskService.findTask(entry.publicId(), DownloadRule.PREPARE_DOWNLOAD_TASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(httpClient.downloadFile(feedFiles.capture(), any(String.class), eq(entry.etag())))
            // 1) rule downloads the discovery file
            .willAnswer(a -> CompletableFuture.completedFuture(Optional.of(gbfsDiscoveryFile)))
            // 2) rule downloads all the other files as well
            .willAnswer(a -> {
                String path = a.getArgument(1).toString();
                path = path.substring(path.lastIndexOf("/") + 1);

                // overwrite assumed downloaded file with our test file to simulate "downloading" for discovered files
                Files.copy(resolveTestFile("lahti_gbfs/" + path), (Path) a.getArgument(0));

                return CompletableFuture.completedFuture(Optional.of(a.getArgument(0)));
            });

        given(taskService.trackTask(entry, dlTask, ProcessingState.UPDATE)).willReturn(dlTask);
        given(s3Client.uploadFile(eq(vacoProperties.s3ProcessingBucket()), targetPath.capture(), sourcePath.capture())).willReturn(CompletableFuture.completedFuture(null));
        given(taskService.trackTask(entry, dlTask, ProcessingState.COMPLETE)).willReturn(dlTask);
        given(taskService.markStatus(entry, dlTask, Status.SUCCESS)).willReturn(dlTask);

        ResultMessage result = rule.execute(entry).join();

        assertThat(result.ruleName(), equalTo(DownloadRule.PREPARE_DOWNLOAD_TASK));

        assertThat("Discovery downloaded all files",
            Streams.collect(feedFiles.getAllValues(), f -> f.getFileName().toString()),
            equalTo(List.of("gbfs.json", "system_information.json", "station_information.json", "vehicle_types.json", "station_status.json", "free_bike_status.json", "system_pricing_plans.json")));
        assertThat(
            "Download of discovered GBFS files resulted in a single archive",
            targetPath.getValue().toString(), equalTo("entries/" + entry.publicId() + "/tasks/prepare.download/rules/prepare.download/output/gbfs.zip"));
    }

    @NotNull
    private static Path resolveTestFile(String testFile) throws URISyntaxException {
        return Path.of(Thread.currentThread().getContextClassLoader().getResource("public/testfiles/" + testFile).toURI());
    }
}
