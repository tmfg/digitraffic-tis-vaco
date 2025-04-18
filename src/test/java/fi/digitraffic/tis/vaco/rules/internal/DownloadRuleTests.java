package fi.digitraffic.tis.vaco.rules.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableDownloadResponse;
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
    @Mock
    private EntryService entryService;
    @Mock
    private FeatureFlagsService featureFlagsService;

    @Captor
    private ArgumentCaptor<Path> tempFilePath;
    @Captor
    private ArgumentCaptor<S3Path> targetPath;
    @Captor
    private ArgumentCaptor<Path> sourcePath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        vacoProperties = TestObjects.vacoProperties();
        rule = new DownloadRule(objectMapper, taskService, vacoProperties, httpClient, s3Client, findingService, entryService, featureFlagsService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskService, httpClient, s3Client, findingService, entryService, featureFlagsService);
    }

    @Test
    void ruleExecutionForGtfs() throws URISyntaxException {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry(TransitDataFormat.GTFS.fieldName());
        Task dlTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();
        Path gtfsTestFile = resolveTestFile("padasjoen_kunta.zip");
        DownloadResponse response = ImmutableDownloadResponse.builder().body(gtfsTestFile).result(DownloadResponse.Result.OK).build();

        given(taskService.findTask(entry.publicId(), DownloadRule.PREPARE_DOWNLOAD_TASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(featureFlagsService.isFeatureFlagEnabled("tasks.prepareDownload.skipDownloadOnStaleETag")).willReturn(true);
        given(httpClient.downloadFile(tempFilePath.capture(), eq(entry.url()), eq(entry))).willAnswer(a -> CompletableFuture.completedFuture(response));
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
        Task dlTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();
        Path gbfsDiscoveryFile = resolveTestFile("lahti_gbfs/gbfs.json");
        String newEtag = "W/new etag";
        DownloadResponse response = ImmutableDownloadResponse.builder()
            .etag(newEtag)
            .body(gbfsDiscoveryFile)
            .result(DownloadResponse.Result.OK)
            .build();

        given(taskService.findTask(entry.publicId(), DownloadRule.PREPARE_DOWNLOAD_TASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(featureFlagsService.isFeatureFlagEnabled("tasks.prepareDownload.skipDownloadOnStaleETag")).willReturn(true);
        given(httpClient.downloadFile(feedFiles.capture(), any(String.class), eq(entry)))
            // 1) rule downloads the discovery file
            .willAnswer(a -> CompletableFuture.completedFuture(response))
            // 2) rule downloads all the other files as well
            .willAnswer(a -> {
                String path = a.getArgument(1).toString();
                path = path.substring(path.lastIndexOf("/") + 1);

                // overwrite assumed downloaded file with our test file to simulate "downloading" for discovered files
                Files.copy(resolveTestFile("lahti_gbfs/" + path), (Path) a.getArgument(0));

                return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().body((Path) a.getArgument(0)).result(DownloadResponse.Result.OK).build());
            });

        given(entryService.updateEtag(entry, newEtag)).willReturn(ImmutableEntry.copyOf(entry).withEtag(newEtag));
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

    @Test
    void cancelsTaskIfPreviousContextEntryHasSameETag() {
        String executionContext = "context can be anything";
        String sharedETag = "foobaretag";
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry(TransitDataFormat.GBFS.fieldName()).context(executionContext).etag(sharedETag);
        Task dlTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(dlTask).build();
        Entry previousEntry = ImmutableEntry.copyOf(entry).withPublicId("höpöhöpö");

        given(taskService.findTask(entry.publicId(), DownloadRule.PREPARE_DOWNLOAD_TASK)).willReturn(Optional.of(dlTask));
        given(taskService.trackTask(entry, dlTask, ProcessingState.START)).willReturn(dlTask);
        given(featureFlagsService.isFeatureFlagEnabled("tasks.prepareDownload.skipDownloadOnStaleETag")).willReturn(true);
        given(entryService.findLatestEntryForContext(entry.businessId(), entry.context())).willReturn(Optional.of(previousEntry));
        given(taskService.trackTask(entry, dlTask, ProcessingState.COMPLETE)).willReturn(dlTask);
        given(taskService.markStatus(entry, dlTask, Status.CANCELLED)).willReturn(dlTask);

        ResultMessage result = rule.execute(entry).join();

        assertThat(result.uploadedFiles().isEmpty(), equalTo(true));
    }

    @NotNull
    private static Path resolveTestFile(String testFile) throws URISyntaxException {
        return Path.of(Thread.currentThread().getContextClassLoader().getResource("public/testfiles/" + testFile).toURI());
    }
}
