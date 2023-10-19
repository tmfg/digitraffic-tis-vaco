package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.aws.S3Packager;
import fi.digitraffic.tis.vaco.conversion.model.ConversionReport;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskResult;
import fi.digitraffic.tis.vaco.process.model.TaskResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ConversionService {
    public static final String PHASE = TaskCategory.CONVERSION.getName();
    public static final String RULESET_SELECTION_SUBTASK = "conversion.rulesets";
    public static final String EXECUTION_SUBTASK = "conversion.execute";
    public static final String OUTPUT_VALIDATION_SUBTASK = "conversion.outputvalidation";

    public static final List<String> ALL_SUBTASKS = List.of(
        RULESET_SELECTION_SUBTASK,
        EXECUTION_SUBTASK,
        OUTPUT_VALIDATION_SUBTASK);

    private final TaskService taskService;
    private final RulesetService rulesetService;

    private final S3Packager s3Packager;

    public ConversionService(TaskService taskService,
                             RulesetService rulesetService,
                             S3Packager s3Packager) {
        this.taskService = Objects.requireNonNull(taskService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.s3Packager = s3Packager;
    }

    public void convert(ImmutableConversionJobMessage jobDescription) {
        Entry entry = jobDescription.entry();
        TaskResult<Set<Ruleset>> conversionRulesets = selectRulesets(jobDescription.entry());

        executeRules(jobDescription.entry(), conversionRulesets.result());

        String packageFileName = PHASE + "_results";
        s3Packager.producePackage(
            entry,
            S3Artifact.getTaskPath(entry.publicId(), PHASE),
            S3Artifact.getPackagePath(entry.publicId(), packageFileName),
            packageFileName).join();
    }

    @VisibleForTesting
    TaskResult<Set<Ruleset>> selectRulesets(Entry entry) {
        ImmutableTask task = taskService.trackTask(taskService.findTask(entry.id(), RULESET_SELECTION_SUBTASK), ProcessingState.START);

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
            entry.businessId(),
            Type.CONVERSION_SYNTAX,
            Streams.map(entry.validations(), ValidationInput::name).toSet());

        taskService.trackTask(task, ProcessingState.COMPLETE);

        return ImmutableTaskResult.of(RULESET_SELECTION_SUBTASK, rulesets);
    }

    @VisibleForTesting
    ImmutableTaskResult<List<ConversionReport>> executeRules(Entry queueEntry, Set<Ruleset> conversionRulesets) {
        // TODO: when exact conversion implementations will be made, they will be executed here
        return ImmutableTaskResult.of(EXECUTION_SUBTASK, null);
    }

}
