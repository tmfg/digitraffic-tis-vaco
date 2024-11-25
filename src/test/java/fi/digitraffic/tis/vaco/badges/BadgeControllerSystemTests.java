package fi.digitraffic.tis.vaco.badges;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class BadgeControllerSystemTests extends SpringBootIntegrationTestBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private HttpServer server;

    @Autowired
    private BadgeController badgeController;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private RecordMapper recordMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestListener testListener;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private VacoProperties vacoProperties;

    @Mock
    private HttpServletResponse response;

    @BeforeAll
    static void beforeAll() {
        createQueue("vaco-errors");
        createQueue("rules-results");
        createQueue("rules-processing-gtfs-canonical");
        createQueue("DLQ-rules-processing");
    }

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        CreateBucketResponse r = createBucket(vacoProperties.s3ProcessingBucket());
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(response);
    }

    public void startServer() throws IOException {
        InetSocketAddress address = new InetSocketAddress(0);
        server = HttpServer.create(address, 0);
        server.createContext("/testfile", new FileHandler());
        server.setExecutor(null);
        server.start();
        logger.debug("Server started");
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            logger.debug("Server stopped");
        }
    }

    static class FileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String filePath = "rule/results/gtfs/gtfs.zip";
            Path path;
            try {
                path = resolveTestFile(filePath);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            byte[] fileBytes = Files.readAllBytes(path);

            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }

    @Test
    void newEntry_statusIsReceived() throws InterruptedException {
        Entry entry = TestObjects.anEntry("gtfs").build();
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));
        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(), Optional.empty(), Optional.empty())
            .tasks(List.of())
            .build();

        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        verifyBadges(createdEntry, List.of(Status.RECEIVED));
    }

    @Test
    void newEntry_statusIsProcessing() throws InterruptedException {
        Entry entry = TestObjects.anEntry("gtfs").build();
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));

        long until = System.currentTimeMillis() + 200;

        while (until < System.currentTimeMillis()) {
            if (assertBadgeIs(Status.PROCESSING, entryRecord)) {
                break;
            }
        }
    }

    @Test
    void newEntry_statusIsCancelled() throws InterruptedException {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task downloadTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100);
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 200);
        Entry entry = entryBuilder.addTasks(downloadTask, validationTask).build();
        assertThat(entry.tasks(), not(empty()));
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));
        List<Task> tasks1 = entry.tasks();
        assertThat(tasks1, not(empty()));
        List<TaskRecord> tasks = taskRepository.createTasks(entryRecord.get(), tasks1);
        assertThat(tasks, not(empty()));

        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(),Optional.empty(), Optional.empty())
            .tasks(Streams.collect(tasks, recordMapper::toTask))
            .build();
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(createdEntry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        verifyBadges(createdEntry, List.of(Status.CANCELLED, Status.FAILED, Status.CANCELLED));

    }

    @Test
    void newEntry_statusIsSuccess() throws InterruptedException, IOException {
        startServer();
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task downloadTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100);
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 200);
        Entry entry = entryBuilder.addTasks(downloadTask, validationTask)
            .url("http://localhost:" + server.getAddress().getPort() + "/testfile")
            .build();
        assertThat(entry.tasks(), not(empty()));
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));
        List<Task> tasks1 = entry.tasks();
        assertThat(tasks1, not(empty()));
        List<TaskRecord> tasks = taskRepository.createTasks(entryRecord.get(), tasks1);
        assertThat(tasks, not(empty()));

        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(),Optional.empty(), Optional.empty())
            .tasks(Streams.collect(tasks, recordMapper::toTask))
            .build();
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(createdEntry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        messagingService.submitProcessingJob(job);

        stopServer();

        verifyBadges(createdEntry, List.of(Status.SUCCESS, Status.SUCCESS, Status.SUCCESS));
    }

    @Test
    void newEntry_statusIsWarning() throws InterruptedException, IOException, URISyntaxException {
        startServer();
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task downloadTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100);
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 200);
        Entry entry = entryBuilder.addTasks(downloadTask, validationTask)
            .url("http://localhost:" + server.getAddress().getPort() + "/testfile")
            .build();
        assertThat(entry.tasks(), not(empty()));
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));
        List<Task> tasks1 = entry.tasks();
        assertThat(tasks1, not(empty()));
        List<TaskRecord> tasks = taskRepository.createTasks(entryRecord.get(), tasks1);
        assertThat(tasks, not(empty()));

        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(),Optional.empty(), Optional.empty())
            .tasks(Streams.collect(tasks, recordMapper::toTask))
            .build();
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(createdEntry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();

        uploadFile(createdEntry.publicId(), "rule/results/gtfs/warning/report.json");
        messagingService.submitProcessingJob(job);

        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        stopServer();

        verifyBadges(createdEntry, List.of(Status.WARNINGS, Status.SUCCESS, Status.WARNINGS));

    }

    @Test
    void newEntry_statusIsError() throws InterruptedException, IOException, URISyntaxException {
        startServer();
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task downloadTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100);
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 200);
        Entry entry = entryBuilder.addTasks(downloadTask, validationTask)
            .url("http://localhost:" + server.getAddress().getPort() + "/testfile")
            .build();
        assertThat(entry.tasks(), not(empty()));
        Optional<EntryRecord> entryRecord = entryRepository.create(entry, Optional.empty(), Optional.empty());
        assertThat(entryRecord.isPresent(), equalTo(true));
        List<Task> tasks1 = entry.tasks();
        assertThat(tasks1, not(empty()));
        List<TaskRecord> tasks = taskRepository.createTasks(entryRecord.get(), tasks1);
        assertThat(tasks, not(empty()));

        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(),Optional.empty(), Optional.empty())
            .tasks(Streams.collect(tasks, recordMapper::toTask))
            .build();
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(createdEntry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();


        uploadFile(createdEntry.publicId(), "rule/results/gtfs/error/report.json");

        messagingService.submitProcessingJob(job);

        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        stopServer();

        verifyBadges(createdEntry, List.of(Status.ERRORS, Status.SUCCESS, Status.ERRORS));

    }

    public void uploadFile(String publicId, String localFilePath) throws URISyntaxException {

        String bucketName = vacoProperties.s3ProcessingBucket();
        S3Path s3path = S3Artifact.getRuleDirectory(publicId, RuleName.GTFS_CANONICAL, RuleName.GTFS_CANONICAL).resolve("output/report.json");

        CompletableFuture<PutObjectResponse> uploadedResponse = s3Client.uploadFile(bucketName, s3path, resolveTestFile(localFilePath));
        uploadedResponse.join();

        testListener.setResultConverter(publicId, jobMessage -> converter(jobMessage, Map.of(s3path.asUri(bucketName), List.of("report"))));
    }


    @NotNull
    private static Path resolveTestFile(String testFile) throws URISyntaxException {
        return Path.of(Thread.currentThread().getContextClassLoader().getResource(testFile).toURI());
    }

    private ResultMessage converter(ValidationRuleJobMessage jobMessage, Map<String, List<String>> uploadedFiles) {
        return ImmutableResultMessage.of(
            jobMessage.entry().publicId(),
            Objects.requireNonNull(jobMessage.task().id()),
            jobMessage.source(),
            jobMessage.inputs(),
            jobMessage.outputs(),
            uploadedFiles
        );
    }


    private void verifyBadges(Entry entry, List<Status> expectedStatuses) {
        List<ClassPathResource> badges = getBadges(entry);
        verify(response, times(badges.size())).addHeader(eq(HttpHeaders.CONTENT_DISPOSITION), contentDisposition.capture());
        assertThat(contentDisposition.getAllValues(), equalTo(Streams.collect(expectedStatuses, s -> { return "inline; filename=\"" + s.fieldName() + ".svg\"";})));
    }

    private List<ClassPathResource> getBadges(Entry entry) {

        List<ClassPathResource> list = new ArrayList<>();
        ClassPathResource entryBadge = badgeController.entryBadge(entry.publicId(), response);
        assertThat(entryBadge, notNullValue());
        list.add(entryBadge);

        entry.tasks().forEach(t -> {
            ClassPathResource taskBadge = badgeController.taskBadge(entry.publicId(), t.name(), response);
            assertThat(taskBadge, notNullValue());
            list.add(taskBadge);
        });

        return list;
    }


    private void waitForEntryProcessingToFinish(String publicId, int maxWait) throws InterruptedException {
        long until = System.currentTimeMillis() + maxWait;
        while (entryRepository.findByPublicId(publicId).map(EntryRecord::completed).isEmpty()
            && (System.currentTimeMillis() < until)) {
            Thread.sleep(10000);
        }
    }

    boolean assertBadgeIs(Status status, Optional<EntryRecord> entryRecord) throws InterruptedException {
        ClassPathResource cpr = badgeController.entryBadge(entryRecord.get().publicId(), response);

        if (cpr.exists()) {
            String expectedName = status.fieldName();
            return true;
        } else {
            Thread.sleep(10);
            return false;
        }
    }

    @Captor
    private ArgumentCaptor<String> contentDisposition;

}
