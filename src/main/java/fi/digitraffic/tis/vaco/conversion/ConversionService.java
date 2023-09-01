package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.model.ConversionReport;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskData;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskResult;
import fi.digitraffic.tis.vaco.process.model.TaskResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversionService {

    public static final String RULESET_SELECTION_SUBTASK = "conversion.rulesets";
    public static final String EXECUTION_SUBTASK = "conversion.execute";
    public static final String OUTPUT_VALIDATION_SUBTASK = "conversion.outputvalidation";

    public static final List<String> ALL_SUBTASKS = List.of(
        RULESET_SELECTION_SUBTASK,
        EXECUTION_SUBTASK,
        OUTPUT_VALIDATION_SUBTASK);

    Logger logger = LoggerFactory.getLogger(getClass());
    private final S3Client s3ClientUtility;
    private final TaskService taskService;
    private final RulesetService rulesetService;
    private final Map<String, Rule> rules;

    public ConversionService(S3Client s3ClientUtility,
                             TaskService taskService,
                             List<Rule> rules,
                             RulesetService rulesetService) {
        this.s3ClientUtility = Objects.requireNonNull(s3ClientUtility);
        this.taskService = Objects.requireNonNull(taskService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
    }

    public ImmutableJobResult convert(ImmutableConversionJobMessage jobDescription) {

        TaskResult<Set<Ruleset>> conversionRulesets = selectRulesets(jobDescription.message());

        TaskResult<List<ConversionReport>> conversionReports = executeRules(jobDescription.message(), conversionRulesets.result());

        return ImmutableJobResult.builder()
            .addResults(conversionRulesets, conversionReports)
            .build();
    }

    @VisibleForTesting
    TaskResult<Set<Ruleset>> selectRulesets(Entry entry) {
        ImmutableTaskData<Ruleset> phaseData = ImmutableTaskData.of(
            taskService.trackTask(taskService.findTask(entry.id(), RULESET_SELECTION_SUBTASK), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
            entry.businessId(),
            Type.CONVERSION_SYNTAX,
            Streams.map(entry.validations(), ValidationInput::name).toSet());

        phaseData.withTask(taskService.trackTask(phaseData.task(), ProcessingState.COMPLETE));

        return ImmutableTaskResult.of(RULESET_SELECTION_SUBTASK, rulesets);
    }

    @VisibleForTesting
    ImmutableTaskResult<List<ConversionReport>> executeRules(Entry queueEntry, Set<Ruleset> conversionRulesets) {
        // TODO: when exact conversion implementations will be made, they will be executed here
        return ImmutableTaskResult.of(EXECUTION_SUBTASK, null);
    }

    public List<String> listSubTasks() {
        return ALL_SUBTASKS;
    }
}
