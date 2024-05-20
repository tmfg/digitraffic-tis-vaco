package fi.digitraffic.tis.vaco.process;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
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

    private ObjectMapper objectMapper;

    private RecordMapper recordMapper;

    private EntryRecord entry;

    private ImmutableRuleset gtfsCanonicalRuleset;
    private ImmutableRuleset gtfs2NetexRuleset;
    private ImmutableRuleset netexEnturRuleset;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        recordMapper = new RecordMapper(objectMapper);
        taskService = new TaskService(taskRepository, rulesetService, cachingService, recordMapper);
        entry = ImmutableEntryRecord.of(
                1000000L,
                NanoIdUtils.randomNanoId(),
                "entry",
                TransitDataFormat.GTFS.fieldName(),
                TestConstants.EXAMPLE_URL,
                Constants.FINTRAFFIC_BUSINESS_ID);
        // these model "download, validate, convert, re-validate" pattern we want to support
        // the data should match with contents of R__seed_data.sql so be careful if you need to edit these
        gtfsCanonicalRuleset = ImmutableRuleset.of(
                entry.id(),
                RuleName.GTFS_CANONICAL,
                "GTFS Canonical",
                Category.GENERIC,
                Type.VALIDATION_SYNTAX,
                TransitDataFormat.GTFS)
            .withBeforeDependencies(List.of(DownloadRule.PREPARE_DOWNLOAD_TASK));
        gtfs2NetexRuleset = ImmutableRuleset.of(
                entry.id(),
                RuleName.GTFS2NETEX_FINTRAFFIC,
                "GTFS2NETEX Fintraffic",
                Category.GENERIC,
                Type.CONVERSION_SYNTAX,
                TransitDataFormat.GTFS)
            .withBeforeDependencies(List.of(DownloadRule.PREPARE_DOWNLOAD_TASK, RuleName.GTFS_CANONICAL))
            .withAfterDependencies(List.of(RuleName.NETEX_ENTUR));
        netexEnturRuleset = ImmutableRuleset.of(
                entry.id(),
                RuleName.NETEX_ENTUR,
                "NeTEx Entur",
                Category.GENERIC,
                Type.VALIDATION_SYNTAX,
                TransitDataFormat.NETEX)
            .withBeforeDependencies(List.of(DownloadRule.PREPARE_DOWNLOAD_TASK));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskRepository, packagesService, rulesetService);
    }

    @Test
    void generatesAllAppropriateTasks() {
        givenAvailableRulesets(Set.of(gtfsCanonicalRuleset, gtfs2NetexRuleset));
        given(taskRepository.findValidationInputs(entry)).willReturn(List.of(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL)));
        given(taskRepository.findConversionInputs(entry)).willReturn(List.of(ImmutableConversionInput.of(RuleName.GTFS2NETEX_FINTRAFFIC)));
        given(rulesetService.findByName(anyString())).willAnswer(a -> {
            return switch (a.getArgument(0).toString()) {
                case RuleName.GTFS_CANONICAL -> Optional.of(gtfsCanonicalRuleset);
                case RuleName.GTFS2NETEX_FINTRAFFIC -> Optional.of(gtfs2NetexRuleset);
                case RuleName.NETEX_ENTUR -> Optional.of(netexEnturRuleset);
                default -> Optional.empty();
            };
        });

        List<ImmutableTask> expectedTasks = List.of(
            ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100),
            ImmutableTask.of(RuleName.GTFS_CANONICAL, 200),
            ImmutableTask.of(RuleName.GTFS2NETEX_FINTRAFFIC, 300),
            ImmutableTask.of(RuleName.NETEX_ENTUR, 400)
        );

        assertThat(taskService.resolveTasks(entry), equalTo(expectedTasks));
    }

    private void givenAvailableRulesets(Set<Ruleset> gtfsCanonicalRuleset) {
        given(rulesetService.selectRulesets(entry.businessId()))
            .willReturn(gtfsCanonicalRuleset);
    }
}
