package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.S3ClientUtility;
import fi.digitraffic.tis.http.HttpClientUtility;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.delegator.model.Subtask;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhaseResult;
import fi.digitraffic.tis.vaco.process.model.JobMessage;
import fi.digitraffic.tis.vaco.process.model.JobResult;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.process.model.PhaseResult;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);
    private final VacoProperties vacoProperties;
    private final QueueHandlerService queueHandlerService;
    private final HttpClientUtility httpClientUtility;
    private final S3ClientUtility s3ClientUtility;
    private final RulesetRepository rulesetRepository;
    private final Map<String, Rule> rules;
    private final ErrorHandlerService errorHandlerService;

    public ValidationService(VacoProperties vacoProperties,
                             QueueHandlerService queueHandlerService,
                             HttpClientUtility httpClient,
                             S3ClientUtility s3ClientUtility,
                             RulesetRepository rulesetRepository,
                             List<Rule> rules,
                             ErrorHandlerService errorHandlerService) {
        this.vacoProperties = vacoProperties;
        this.queueHandlerService = queueHandlerService;
        this.httpClientUtility = httpClient;
        this.s3ClientUtility = s3ClientUtility;
        this.rulesetRepository = rulesetRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
        this.errorHandlerService = errorHandlerService;
    }

    public JobResult validate(JobMessage jobDescription) throws ValidationProcessException {
        PhaseResult<ImmutableFileReferences> s3path = downloadFile(jobDescription.message());

        PhaseResult<Set<Ruleset>> validationRulesets = selectRulesets(jobDescription.message());

        ImmutablePhaseResult<List<ValidationReport>> validationReports = executeRules(jobDescription.message(), s3path.result(), validationRulesets.result());

        return ImmutableJobResult.builder()
                .addResults(s3path, validationRulesets, validationReports)
                .build();
    }

    private static ImmutablePhase uninitializedPhase(Long entryId, String phaseName) {
        return ImmutablePhase.builder()
                .entryId(entryId)
                .name(phaseName)
                .priority(Subtask.VALIDATION.priority)
                .build();
    }

    @VisibleForTesting
    PhaseResult<ImmutableFileReferences> downloadFile(QueueEntry queueEntry) {
        ImmutablePhaseData<ImmutableFileReferences> phaseData = ImmutablePhaseData.of(
                queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), DOWNLOAD_PHASE), ProcessingState.START));

        Path downloadDir = Paths.get(vacoProperties.getTemporaryDirectory(), queueEntry.publicId(), phaseData.phase().name());
        Path filePath = s3ClientUtility.createDownloadTempFile(downloadDir,
                                                               queueEntry.format());

        return httpClientUtility.downloadFile(filePath, queueEntry.url(), queueEntry.etag())
            .thenApply(wrapHttpResult(phaseData))
            .thenCompose(uploadToS3(queueEntry))
            .thenApply(completeDownloadPhase())
            .join(); // wait for finish - might be temporary
    }

    private Function<HttpResponse<Path>, ImmutablePhaseData<ImmutableFileReferences>> wrapHttpResult(ImmutablePhaseData<ImmutableFileReferences> phaseData) {
        return path -> phaseData
                // download done -> update phase
                .withPhase(queueHandlerService.reportPhase(phaseData.phase(), ProcessingState.UPDATE))
                .withPayload(ImmutableFileReferences.builder().localPath(path.body()).build());
    }

    private Function<ImmutablePhaseData<ImmutableFileReferences>, CompletableFuture<ImmutablePhaseData<ImmutableFileReferences>>> uploadToS3(QueueEntry queueEntry) {
        return phaseData -> {
            String targetPath = "entries/" + queueEntry.publicId() + "/download/" + queueEntry.format() + ".original";
            String bucketPath = vacoProperties.getS3processingBucket();
            Path sourcePath = phaseData.payload().localPath();

            return s3ClientUtility.uploadFile(bucketPath, targetPath, sourcePath)
                    .thenApply(u -> phaseData // upload done -> update phase
                        .withPhase(queueHandlerService.reportPhase(phaseData.phase(), ProcessingState.UPDATE)));
        };
    }

    private Function<PhaseData<ImmutableFileReferences>, PhaseResult<ImmutableFileReferences>> completeDownloadPhase() {
        return phaseData -> {
            ImmutableFileReferences fileRefs = phaseData.payload();
            LOGGER.info("S3 path: {}, upload status: {}", fileRefs.s3Path(), fileRefs.upload());
            // download complete, mark to database as complete and unwrap payload
            queueHandlerService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE);
            return ImmutablePhaseResult.of(DOWNLOAD_PHASE, fileRefs);
        };
    }

    @VisibleForTesting
    PhaseResult<Set<Ruleset>> selectRulesets(QueueEntry queueEntry) {
        ImmutablePhaseData<Ruleset> phaseData = ImmutablePhaseData.of(
                queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), RULESET_SELECTION_PHASE), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetRepository.findRulesets(queueEntry.businessId(), Type.VALIDATION_SYNTAX);

        phaseData.withPhase(queueHandlerService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE));

        return ImmutablePhaseResult.of(RULESET_SELECTION_PHASE, rulesets);
    }

    @VisibleForTesting
    ImmutablePhaseResult<List<ValidationReport>> executeRules(QueueEntry queueEntry, ImmutableFileReferences fileReferences, Set<Ruleset> validationRulesets) {
        PhaseData<FileReferences> phaseData = ImmutablePhaseData.<FileReferences>builder()
                .phase(queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), EXECUTION_PHASE), ProcessingState.START))
                .payload(fileReferences)
                .build();

        List<ValidationReport> reports = validationRulesets.parallelStream()
                .map(this::findMatchingRule)
                .filter(Optional::isPresent)
                .map(r -> r.get().execute(queueEntry, phaseData))
                .map(CompletableFuture::join)
                .toList();
        // everything's done at this point because of the ::join call, complete phase and return
        queueHandlerService.reportPhase(phaseData.phase(), ProcessingState.COMPLETE);
        return ImmutablePhaseResult.of(EXECUTION_PHASE, reports);
    }

    private Optional<Rule> findMatchingRule(Ruleset validationRule) {
        String identifyingName = validationRule.identifyingName();
        Optional<Rule> rule = Optional.ofNullable(rules.get(identifyingName));
        if (rule.isEmpty()) {
            LOGGER.error("No matching rule found with identifying name '{}' from available {}", identifyingName, rules.keySet());
        }
        return rule;
    }

}
