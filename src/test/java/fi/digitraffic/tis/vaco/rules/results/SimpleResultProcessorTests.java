package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SimpleResultProcessorTests {

    /**
     * This is ignored because in mocking the files residing in S3 are not available for filtering.
     */
    private final static String IGNORED_PATH_VALUE = "IGNORED ON PURPOSE. If you see this in output, something is broken.";

    private ObjectMapper objectMapper;
    private Entry entry;
    private Task conversionTask;
    private VacoProperties vacoProperties;

    private ResultMessage gtfs2netexMessage;

    private SimpleResultProcessor simpleResultProcessor;
    @Mock private PackagesService packagesService;
    @Mock private TaskService taskService;

    @BeforeEach
    void setUp() {
        simpleResultProcessor = new SimpleResultProcessor(packagesService, taskService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModules(new GuavaModule());

        vacoProperties = TestObjects.vacoProperties();

        entry = entryWithTask(e -> ImmutableTask.of(e.id(), RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0, 100).withId(9_000_000L));
        conversionTask = entry.tasks().get(0);
        Map<String, List<String>> uploadedFiles = Map.of(
            "file.txt", List.of("all", "debug"),
            "another.txt", List.of("debug"));
        gtfs2netexMessage = asResultMessage(entry, conversionTask, uploadedFiles);
    }

    @Test
    void producesPackagesByDefault() {
        givenPackageIsCreated("all", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask.id(), "all", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask.id(), "debug", IGNORED_PATH_VALUE));
        givenTaskStatusIsMarkedAs(Status.SUCCESS);

        simpleResultProcessor.processResults(gtfs2netexMessage, entry, conversionTask);
    }

    private BDDMockito.BDDMyOngoingStubbing<Package> givenPackageIsCreated(String packageName, Entry entry, Task task) {
        return given(packagesService.createPackage(
            eq(entry),
            eq(task),
            eq(packageName),
            eq(S3Path.of("outputs")),
            eq(packageName + ".zip"),
            any()));
    }

    private void givenTaskStatusIsMarkedAs(Status status) {
        given(taskService.markStatus(any(), eq(status))).will(a -> a.getArgument(0));
    }

    // TODO: These factory methods are helpers for rule result processing tests, could extract a test-only helper class

    private Entry entryWithTask(Function<Entry, Task> taskCreator) {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        ImmutableEntry entry = entryBuilder.build();
        entry = entry.withTasks(taskCreator.apply(entry));
        return entry;
    }

    @NotNull
    private ResultMessage asResultMessage(Entry entry, Task conversionTask, Map<String, ? extends List<String>> uploadedFiles) {
        return ImmutableResultMessage.builder()
            .ruleName(RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0)
            .entryId(entry.publicId())
            .taskId(conversionTask.id())
            .inputs("s3://" + vacoProperties.s3ProcessingBucket() + "/inputs")
            .outputs("s3://" + vacoProperties.s3ProcessingBucket() + "/outputs")
            .uploadedFiles(uploadedFiles)
            .build();
    }

}
