package fi.digitraffic.tis.vaco.process;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, packagesService, rulesetService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskRepository, packagesService, rulesetService);
    }

    @Test
    void generatesAllAppropriateTasks() {
        ImmutableEntry entry = ImmutableEntry.of(
                TransitDataFormat.GTFS.fieldName(),
                TestConstants.EXAMPLE_URL,
                Constants.FINTRAFFIC_BUSINESS_ID)
            .withId(1000000L)
            .withPublicId(NanoIdUtils.randomNanoId())
            .withValidations(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL_4_1_0));
        Ruleset gtfsCanonicalRuleset = ImmutableRuleset.of(
            entry.id(),
            RuleName.GTFS_CANONICAL_4_1_0,
            "bleh",
            Category.GENERIC,
            Type.VALIDATION_SYNTAX,
            TransitDataFormat.GTFS);

        BDDMockito.given(rulesetService.selectRulesets(entry.businessId(), Type.VALIDATION_SYNTAX, TransitDataFormat.GTFS, Set.of()))
            .willReturn(Set.of(gtfsCanonicalRuleset));

        BDDMockito.given(rulesetService.selectRulesets(entry.businessId(), Type.CONVERSION_SYNTAX, TransitDataFormat.GTFS, Set.of()))
            .willReturn(Set.of());

        BDDMockito.given(rulesetService.findByName(RuleName.GTFS_CANONICAL_4_1_0)).willReturn(Optional.of(gtfsCanonicalRuleset));

        List<Task> tasks = taskService.resolveTasks(entry);
        tasks.forEach(System.out::println);
    }
}
