package fi.digitraffic.tis.vaco.process;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {

    private TaskService taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PackagesService packagesService;

    @Mock
    private RulesetService rulesetService;

    @Mock
    private CachingService cachingService;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    ObjectMapper objectMapper;

    private ImmutableEntry entry;
    private ImmutableRuleset gtfsCanonicalRuleset;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, packagesService, rulesetService, cachingService);
        entry = ImmutableEntry.of(
                "entry",
                TransitDataFormat.GTFS.fieldName(),
                TestConstants.EXAMPLE_URL,
                Constants.FINTRAFFIC_BUSINESS_ID)
            .withId(1000000L)
            .withPublicId(NanoIdUtils.randomNanoId());
        gtfsCanonicalRuleset = ImmutableRuleset.of(
                entry.id(),
                RuleName.GTFS_CANONICAL_4_1_0,
                "bleh",
                Category.GENERIC,
                Type.VALIDATION_SYNTAX,
                TransitDataFormat.GTFS)
            .withDependencies(List.of(DownloadRule.DOWNLOAD_SUBTASK, RulesetSubmissionService.VALIDATE_TASK));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskRepository, packagesService, rulesetService);
    }

    @Test
    void generatesAllAppropriateTasks() {
        entry = entry.withValidations(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL_4_1_0));

        givenAvailableRulesets(Type.VALIDATION_SYNTAX, TransitDataFormat.GTFS, Set.of(gtfsCanonicalRuleset));
        given(rulesetService.findByName(RuleName.GTFS_CANONICAL_4_1_0)).willReturn(Optional.of(gtfsCanonicalRuleset));

        List<Task> tasks = taskService.resolveTasks(entry);
        tasks.forEach(System.out::println);
        List<Task> expectedTasks = List.of(
            ImmutableTask.of(entry.id(), DownloadRule.DOWNLOAD_SUBTASK, 100),
            ImmutableTask.of(entry.id(), RulesetSubmissionService.VALIDATE_TASK, 200),
            ImmutableTask.of(entry.id(), RuleName.GTFS_CANONICAL_4_1_0, 201)
        );

        assertThat(taskService.resolveTasks(entry), equalTo(expectedTasks));
    }

    private void givenAvailableRulesets(Type type, TransitDataFormat transitDataFormat, Set<Ruleset> gtfsCanonicalRuleset) {
        given(rulesetService.selectRulesets(entry.businessId()))
            .willReturn(gtfsCanonicalRuleset);
    }
}
