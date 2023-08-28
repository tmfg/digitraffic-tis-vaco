package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.process.PhaseService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseResult;
import fi.digitraffic.tis.vaco.process.model.JobResult;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.process.model.PhaseResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ValidationService {
    public static final String DOWNLOAD_PHASE = "validation.download";
    public static final String RULESET_SELECTION_PHASE = "validation.rulesets";
    public static final String EXECUTION_PHASE = "validation.execute";

    public static final List<String> ALL_SUBPHASES = List.of(DOWNLOAD_PHASE, RULESET_SELECTION_PHASE, EXECUTION_PHASE);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PhaseService phaseService;
    private final RulesetService rulesetService;
    private final HttpClient httpClientUtility;
    private final S3Client s3ClientUtility;
    private final RulesetRepository rulesetRepository;
    private final Map<String, Rule> rules;

    public ValidationService(PhaseService phaseService,
                             RulesetService rulesetService,
                             HttpClient httpClient,
                             S3Client s3ClientUtility,
                             RulesetRepository rulesetRepository,
                             List<Rule> rules) {
        this.phaseService = phaseService;
        this.rulesetService = rulesetService;
        this.httpClientUtility = httpClient;
        this.s3ClientUtility = s3ClientUtility;
        this.rulesetRepository = rulesetRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
    }

    public JobResult validate(ValidationJobMessage jobDescription) throws ValidationProcessException {
        Entry entry = jobDescription.message();
        PhaseResult<ImmutableFileReferences> s3path = downloadFile(entry);

        PhaseResult<Set<Ruleset>> validationRulesets = selectRulesets(entry);

        PhaseResult<List<ValidationReport>> validationReports = executeRules(entry, s3path.result(), validationRulesets.result());

        return ImmutableJobResult.builder()
                .addResults(s3path, validationRulesets, validationReports)
                .build();
    }

    @VisibleForTesting
    PhaseResult<ImmutableFileReferences> downloadFile(Entry queueEntry) {
        ImmutablePhaseData<ImmutableFileReferences> phaseData = ImmutablePhaseData.of(
                phaseService.reportPhase(phaseService.findPhase(queueEntry.id(), DOWNLOAD_PHASE), ProcessingState.START));
        Path tempFilePath = s3ClientUtility.createVacoDownloadTempFile(queueEntry.publicId(),
            queueEntry.format(), phaseData.phase().name());

        return httpClientUtility.downloadFile(tempFilePath, queueEntry.url(), queueEntry.etag())
            .thenApply(wrapHttpResult(phaseData))
            .thenCompose(uploadToS3(queueEntry))
            .thenApply(completeDownloadPhase())
            .join(); // wait for finish - might be temporary
    }

    private Function<HttpResponse<Path>, ImmutablePhaseData<ImmutableFileReferences>> wrapHttpResult(ImmutablePhaseData<ImmutableFileReferences> phaseData) {
        return path -> phaseData
                // download done -> update phase
                .withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.UPDATE))
                .withPayload(ImmutableFileReferences.of(path.body()));
    }

    private Function<ImmutablePhaseData<ImmutableFileReferences>, CompletableFuture<ImmutablePhaseData<ImmutableFileReferences>>> uploadToS3(Entry queueEntry) {
        return phaseData -> {
            String targetPath = S3Artifact.getDownloadPhasePath(queueEntry.publicId(), queueEntry.format() + ".original");
            Path sourcePath = phaseData.payload().localPath();

            return s3ClientUtility.uploadFile(targetPath, sourcePath)
                    .thenApply(u -> phaseData // upload done -> update phase
                        .withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.UPDATE)));
        };
    }

    private Function<PhaseData<ImmutableFileReferences>, PhaseResult<ImmutableFileReferences>> completeDownloadPhase() {
        return phaseData -> {
            ImmutableFileReferences fileRefs = phaseData.payload();
            logger.info("S3 path: {}, upload status: {}", fileRefs.s3Path(), fileRefs.upload());
            // download complete, mark to database as complete and unwrap payload
            phaseService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE);
            return ImmutablePhaseResult.of(DOWNLOAD_PHASE, fileRefs);
        };
    }

    @VisibleForTesting
    PhaseResult<Set<Ruleset>> selectRulesets(Entry entry) {
        ImmutablePhaseData<Ruleset> phaseData = ImmutablePhaseData.of(
                phaseService.reportPhase(phaseService.findPhase(entry.id(), RULESET_SELECTION_PHASE), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
                entry.businessId(),
                Type.VALIDATION_SYNTAX,
                Streams.map(entry.validations(), ValidationInput::name).toSet());

        phaseData.withPhase(phaseService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE));

        return ImmutablePhaseResult.of(RULESET_SELECTION_PHASE, rulesets);
    }

    @VisibleForTesting
    ImmutablePhaseResult<List<ValidationReport>> executeRules(Entry entry, ImmutableFileReferences fileReferences, Set<Ruleset> validationRulesets) {
        PhaseData<FileReferences> phaseData = ImmutablePhaseData.<FileReferences>builder()
                .phase(phaseService.reportPhase(phaseService.findPhase(entry.id(), EXECUTION_PHASE), ProcessingState.START))
                .payload(fileReferences)
                .build();

        Map<String, ValidationInput> configs = Streams.collect(entry.validations(), ValidationInput::name, Function.identity());

        List<ValidationReport> reports = validationRulesets.parallelStream()
                .map(this::findMatchingRule)
                .filter(Optional::isPresent)
                .map(r -> r.get().execute(entry, r.map(x -> configs.get(x.getIdentifyingName())), phaseData))
                .map(CompletableFuture::join)
                .toList();
        // everything's done at this point because of the ::join call, complete phase and return
        phaseService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE);
        return ImmutablePhaseResult.of(EXECUTION_PHASE, reports);
    }

    private Optional<Rule> findMatchingRule(Ruleset validationRule) {
        String identifyingName = validationRule.identifyingName();
        Optional<Rule> rule = Optional.ofNullable(rules.get(identifyingName));
        if (rule.isEmpty()) {
            logger.error("No matching rule found with identifying name '{}' from available {}", identifyingName, rules.keySet());
        }
        return rule;
    }

    public List<String> listSubPhases() {
        return ALL_SUBPHASES;
    }
}
