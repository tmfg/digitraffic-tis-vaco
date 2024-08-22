package fi.digitraffic.tis.vaco.findings;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.EntryStateService;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.TaskReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class FindingsIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    EntryStateService entryStateService;
    @Autowired
    EntryRepository entryRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private RulesetRepository rulesetRepository;
    @Autowired
    private FindingRepository findingRepository;
    @Autowired
    RecordMapper recordMapper;

    private Task task;
    private EntryRecord entry;
    private Ruleset rule;

    @BeforeEach
    void setUp() {

        ImmutableEntry entryToCreate = TestObjects.anEntry("gtfs").build();
        entry = entryRepository.create(Optional.empty(), entryToCreate).get();
        taskRepository.createTasks(entry, List.of(ImmutableTask.of(RuleName.GTFS_CANONICAL,1)));
        task = taskRepository.findTask(entry.id(), RuleName.GTFS_CANONICAL).get();
        rule = rulesetRepository.findByName(RuleName.GTFS_CANONICAL).get();

        createSystemErrorFinding("invalid_url");
        createSystemErrorFinding("i_o_error");
        createSystemErrorFinding("runtime_exception_in_loader_error");
        createSystemErrorFinding("runtime_exception_in_validator_error");
        createSystemErrorFinding("thread_execution_error");
        createSystemErrorFinding("u_r_i_syntax_error");
        createSystemErrorFinding("trip_distance_exceeds_shape_distance");


    }

    private void createSystemErrorFinding(String error) {
        Finding SystemErrorFinding = TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.ERROR)
            .message(error)
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(SystemErrorFinding);
    }

    @Test
    void testSystemsErrorsAreOverriddenAsWarnings() {

        Map<String, Ruleset> rulesetMap = Map.of(task.name(), rule);
        Optional<ContextRecord> context = Optional.empty();
        TaskReport ruleReport = entryStateService.getTaskReport(task, recordMapper.toEntryBuilder(entry, context).build(), rulesetMap);

        AggregatedFinding invalidUrlError = ruleReport.findings().get(0);
        assertThatSeverityIsError(invalidUrlError, "invalid_url");

        AggregatedFinding ioError = ruleReport.findings().get(1);
        assertThatSeverityIsError(ioError, "i_o_error");

        AggregatedFinding runtimeLoaderError = ruleReport.findings().get(2);
        assertThatSeverityIsError(runtimeLoaderError, "runtime_exception_in_loader_error");

        AggregatedFinding runtimeExceptionError = ruleReport.findings().get(3);
        assertThatSeverityIsError(runtimeExceptionError, "runtime_exception_in_validator_error");

        AggregatedFinding threadExecutionError = ruleReport.findings().get(4);
        assertThatSeverityIsError(threadExecutionError, "thread_execution_error");

        AggregatedFinding uriSyntaxError = ruleReport.findings().get(5);
        assertThatSeverityIsError(uriSyntaxError, "u_r_i_syntax_error");

        AggregatedFinding tripsDistanceError = ruleReport.findings().get(6);
        assertThatSeverityIsError(tripsDistanceError, "trip_distance_exceeds_shape_distance");


    }

    private static void assertThatSeverityIsError(AggregatedFinding Error, String error) {
        assertThat(Error.code(), equalTo(error));
        assertThat(Error.severity(), equalTo("WARNING"));
    }

}

