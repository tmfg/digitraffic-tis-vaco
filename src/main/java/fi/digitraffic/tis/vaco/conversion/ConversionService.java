package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.aws.s3.S3ClientUtility;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.conversion.model.ConversionReport;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.Subtask;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.process.PhaseService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseResult;
import fi.digitraffic.tis.vaco.process.model.PhaseResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversionService {

    public static final String RULESET_SELECTION_PHASE = "conversion.rulesets";
    public static final String EXECUTION_PHASE = "conversion.execute";
    public static final String OUTPUT_VALIDATION_PHASE = "conversion.outputvalidation";

    Logger logger = LoggerFactory.getLogger(getClass());
    private final S3ClientUtility s3ClientUtility;
    private final PhaseService phaseService;
    private final RulesetRepository rulesetRepository;
    private final Map<String, Rule> rules;
    private final VacoProperties vacoProperties;

    private final ErrorHandlerService errorHandlerService;

    public ConversionService(S3ClientUtility s3ClientUtility,
                             PhaseService phaseService, RulesetRepository rulesetRepository,
                             List<Rule> rules,
                             VacoProperties vacoProperties, ErrorHandlerService errorHandlerService) {
        this.s3ClientUtility = s3ClientUtility;
        this.phaseService = phaseService;
        this.rulesetRepository = rulesetRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
        this.vacoProperties = vacoProperties;
        this.errorHandlerService = errorHandlerService;
    }

    public ImmutableJobResult convert(ImmutableConversionJobMessage jobDescription) {

        PhaseResult<Set<Ruleset>> conversionRulesets = selectRulesets(jobDescription.message());

        PhaseResult<List<ConversionReport>> conversionReports = executeRules(jobDescription.message(), conversionRulesets.result());

        return ImmutableJobResult.builder()
            .addResults(conversionRulesets, conversionReports)
            .build();
    }

    private static ImmutablePhase uninitializedPhase(Long entryId, String phaseName) {
        return ImmutablePhase.of(entryId,phaseName, Subtask.CONVERSION.priority);
    }

    private Function<ImmutablePhaseData<ImmutableFileReferences>, CompletableFuture<ImmutablePhaseData<ImmutableFileReferences>>> uploadToS3(Entry queueEntry) {
        return phaseData -> {
            String targetPath = S3Artifact.getConversionPhasePath(queueEntry.publicId(),
                                                         "output",
                                                                  queueEntry.conversion().targetFormat());
            Path sourcePath = phaseData.payload().localPath();

            return s3ClientUtility.uploadFile(targetPath, sourcePath)
                .thenApply(u -> phaseData // upload done -> update phase
                    .withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.UPDATE)));
        };
    }

    @VisibleForTesting
    PhaseResult<Set<Ruleset>> selectRulesets(Entry queueEntry) {
        ImmutablePhaseData<Ruleset> phaseData = ImmutablePhaseData.of(
            phaseService.reportPhase(uninitializedPhase(queueEntry.id(), RULESET_SELECTION_PHASE), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetRepository.findRulesets(queueEntry.businessId(), Type.CONVERSION_SYNTAX);

        phaseData.withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE));

        return ImmutablePhaseResult.of(RULESET_SELECTION_PHASE, rulesets);
    }

    @VisibleForTesting
    ImmutablePhaseResult<List<ConversionReport>> executeRules(Entry queueEntry, Set<Ruleset> conversionRulesets) {
        // TODO: when exact conversion implementations will be made, they will be executed here
        return ImmutablePhaseResult.of(EXECUTION_PHASE, null);
    }

    private Optional<Rule> findMatchingRule(Ruleset ruleset) {
        String identifyingName = ruleset.identifyingName();
        Optional<Rule> ruleOptional = Optional.ofNullable(rules.get(identifyingName));
        if (ruleOptional.isEmpty()) {
            logger.error("No matching rule found with identifying name '{}' from available {}", identifyingName, rules.keySet());
        }
        return ruleOptional;
    }

}
