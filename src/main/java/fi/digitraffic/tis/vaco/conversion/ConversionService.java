package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.model.ConversionReport;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.Subtask;
import fi.digitraffic.tis.vaco.process.PhaseService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseResult;
import fi.digitraffic.tis.vaco.process.model.PhaseResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
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

    public static final String RULESET_SELECTION_PHASE = "conversion.rulesets";
    public static final String EXECUTION_PHASE = "conversion.execute";
    public static final String OUTPUT_VALIDATION_PHASE = "conversion.outputvalidation";

    Logger logger = LoggerFactory.getLogger(getClass());
    private final S3Client s3ClientUtility;
    private final PhaseService phaseService;
    private final RulesetService rulesetService;
    private final Map<String, Rule> rules;

    public ConversionService(S3Client s3ClientUtility,
                             PhaseService phaseService,
                             List<Rule> rules,
                             RulesetService rulesetService) {
        this.s3ClientUtility = Objects.requireNonNull(s3ClientUtility);
        this.phaseService = Objects.requireNonNull(phaseService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
    }

    public ImmutableJobResult convert(ImmutableConversionJobMessage jobDescription) {

        PhaseResult<Set<Ruleset>> conversionRulesets = selectRulesets(jobDescription.message());

        PhaseResult<List<ConversionReport>> conversionReports = executeRules(jobDescription.message(), conversionRulesets.result());

        return ImmutableJobResult.builder()
            .addResults(conversionRulesets, conversionReports)
            .build();
    }

    private static ImmutablePhase uninitializedPhase(Long entryId, String phaseName) {
        return ImmutablePhase.of(entryId, phaseName, Subtask.CONVERSION.priority);
    }

    @VisibleForTesting
    PhaseResult<Set<Ruleset>> selectRulesets(Entry entry) {
        ImmutablePhaseData<Ruleset> phaseData = ImmutablePhaseData.of(
            phaseService.reportPhase(uninitializedPhase(entry.id(), RULESET_SELECTION_PHASE), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
            entry.businessId(),
            Type.CONVERSION_SYNTAX,
            Streams.map(entry.validations(), ValidationInput::name).toSet());

        phaseData.withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE));

        return ImmutablePhaseResult.of(RULESET_SELECTION_PHASE, rulesets);
    }

    @VisibleForTesting
    ImmutablePhaseResult<List<ConversionReport>> executeRules(Entry queueEntry, Set<Ruleset> conversionRulesets) {
        // TODO: when exact conversion implementations will be made, they will be executed here
        return ImmutablePhaseResult.of(EXECUTION_PHASE, null);
    }
}
