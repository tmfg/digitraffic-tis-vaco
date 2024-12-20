package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith(MockitoExtension.class)
class SimpleResultProcessorTests extends ResultProcessorTestBase {

    private Entry entry;

    private Task conversionTask;

    private ResultMessage gtfs2netexMessage;

    private SimpleResultProcessor simpleResultProcessor;

    @Mock
    private S3Client s3Client;

    @Mock
    private FindingService findingService;

    @BeforeEach
    void setUp() {
        VacoProperties vacoProperties = TestObjects.vacoProperties();
        simpleResultProcessor = new SimpleResultProcessor(vacoProperties, packagesService, s3Client, taskService, findingService);

        entry = entryWithTask(e -> ImmutableTask.of(RuleName.GTFS2NETEX_FINTRAFFIC, 100).withId(9_000_000L));
        conversionTask = entry.tasks().get(0);
        Map<String, List<String>> uploadedFiles = Map.of(
            "file.txt", List.of("all", "debug"),
            "another.txt", List.of("debug"));
        gtfs2netexMessage = asResultMessage(vacoProperties, RuleName.GTFS2NETEX_FINTRAFFIC, entry, uploadedFiles);
    }

    @Test
    void producesPackagesByDefault() {
        givenPackageIsCreated("all", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask, "all", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask, "debug", IGNORED_PATH_VALUE));
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, entry.tasks().get(0), ProcessingState.COMPLETE);

        assertThat(simpleResultProcessor.processResults(gtfs2netexMessage, entry, conversionTask), equalTo(true));
    }


}
