package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.PhaseState;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.validation.model.ImmutableResult;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobResult;
import fi.digitraffic.tis.vaco.validation.model.PhaseData;
import fi.digitraffic.tis.vaco.validation.model.Result;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobResult;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import fi.digitraffic.tis.vaco.validation.repository.RuleSetRepository;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);
    private static final String DOWNLOAD_PHASE = "validation.download";
    private static final String RULESET_SELECTION_PHASE = "validation.rulesets";
    private static final String EXECUTION_PHASE = "validation.execute";

    private final VacoProperties vacoProperties;
    private final S3TransferManager s3TransferManager;
    private final QueueHandlerService queueHandlerService;
    private final HttpClient httpClient;
    private final RuleSetRepository rulesetsRepository;
    private final Map<String, Rule> rules;

    public ValidationService(VacoProperties vacoProperties,
                             S3TransferManager s3TransferManager,
                             QueueHandlerService queueHandlerService,
                             HttpClient httpClient,
                             RuleSetRepository rulesetsRepository,
                             List<Rule> rules) {
        this.vacoProperties = vacoProperties;
        this.s3TransferManager = s3TransferManager;
        this.queueHandlerService = queueHandlerService;
        this.httpClient = httpClient;
        this.rulesetsRepository = rulesetsRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
    }

    public ValidationJobResult validate(ImmutableJobDescription jobDescription) throws ValidationProcessException {
        Result<ImmutableFileReferences> s3path = downloadFile(jobDescription.message());

        Result<Set<ValidationRule>> validationRules = selectRulesets(jobDescription.message(), jobDescription);

        List<Result<ValidationReport>> validationReports = executeRules(jobDescription.message(), s3path.result(), validationRules.result());

        return ImmutableValidationJobResult.builder()
                .addResults(s3path, validationRules)
                .addAllResults(validationReports)
                .build();
    }

    private static ImmutablePhase uninitializedPhase(Long entryId, String phaseName) {
        return ImmutablePhase.builder()
                .entryId(entryId)
                .name(phaseName)
                .build();
    }

    @VisibleForTesting
    Result<ImmutableFileReferences> downloadFile(QueueEntry queueEntry) {
        ImmutablePhaseData<ImmutableFileReferences> phaseData = ImmutablePhaseData.of(
                queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), DOWNLOAD_PHASE), PhaseState.START));

        Path downloadFile = createDownloadTempFile(queueEntry);
        HttpRequest request = buildRequest(queueEntry);
        HttpResponse.BodyHandler<Path> bodyHandler = BodyHandlers.ofFile(downloadFile);

        return httpClient.sendAsync(request, bodyHandler)
                .thenApply(wrapHttpResult(phaseData))
            .thenCompose(uploadToS3(queueEntry))
            .thenApply(completeDownloadPhase())
            .join(); // wait for finish - might be temporary
    }

    private Function<HttpResponse<Path>, ImmutablePhaseData<ImmutableFileReferences>> wrapHttpResult(ImmutablePhaseData<ImmutableFileReferences> phaseData) {
        return path -> phaseData
                // download done -> update phase
                .withPhase(queueHandlerService.reportPhase(phaseData.phase(), PhaseState.UPDATE))
                .withPayload(ImmutableFileReferences.builder().localPath(path.body()).build());
    }

    private Path createDownloadTempFile(QueueEntry queueEntry) {
        Path downloadDir = Paths.get(vacoProperties.getTemporaryDirectory(), "vaco", queueEntry.publicId());
        try {
            LOGGER.info("Download path for {} is {}", queueEntry.publicId(), downloadDir);

            Files.createDirectories(downloadDir);
            Path downloadFile = downloadDir.resolve(queueEntry.format() + ".download");
            if (Files.exists(downloadFile)) {
                throw new ValidationProcessException("File already exists! Is the process running twice?");
            }
            return downloadFile;
        } catch (IOException e) {
            throw new ValidationProcessException("Failed to create directories for temporary file, make sure permissions are set correctly for path " + downloadDir, e);
        }
    }

    private static HttpRequest buildRequest(QueueEntry queueEntry) {
        try {
            var builder = HttpRequest.newBuilder()
                .GET()
                .uri(new URI(queueEntry.url()));

            if (queueEntry.etag() != null) {
                builder = builder.header("ETag", queueEntry.etag());
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new ValidationProcessException("URI provided in queue entry is invalid", e);
        }
    }

    private Function<ImmutablePhaseData<ImmutableFileReferences>, CompletableFuture<ImmutablePhaseData<ImmutableFileReferences>>> uploadToS3(QueueEntry queueEntry) {
        return phaseData -> {
            String s3Path = "entries/" + queueEntry.publicId() + "/download/" + queueEntry.format() + ".original";

            UploadFileRequest ufr = UploadFileRequest.builder()
                .putObjectRequest(req -> req.bucket(vacoProperties.getS3processingBucket()).key(s3Path))
                .addTransferListener(LoggingTransferListener.create())
                .source(phaseData.payload().localPath())
                .build();
            FileUpload upload = s3TransferManager.uploadFile(ufr);

            return upload.completionFuture()
                .thenApply(u -> phaseData
                        // upload done -> update phase
                        .withPhase(queueHandlerService.reportPhase(phaseData.phase(), PhaseState.UPDATE)));
        };
    }

    private Function<PhaseData<ImmutableFileReferences>, Result<ImmutableFileReferences>> completeDownloadPhase() {
        return phaseData -> {
            ImmutableFileReferences fileRefs = phaseData.payload();
            LOGGER.info("S3 path: {}, upload status: {}", fileRefs.s3Path(), fileRefs.upload());
            // download complete, mark to database as complete and unwrap payload
            queueHandlerService.reportPhase(phaseData.phase(), PhaseState.COMPLETE);
            return ImmutableResult.of(DOWNLOAD_PHASE, fileRefs);
        };
    }

    @VisibleForTesting
    Result<Set<ValidationRule>> selectRulesets(QueueEntry queueEntry, ImmutableJobDescription jobDescription) {
        ImmutablePhaseData<ValidationRule> phaseData = ImmutablePhaseData.of(
                queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), RULESET_SELECTION_PHASE), PhaseState.START));

        Set<ValidationRule> validationRules = rulesetsRepository.findRulesets(jobDescription.message().businessId());

        phaseData.withPhase(queueHandlerService.reportPhase(phaseData.phase(), PhaseState.COMPLETE));

        return ImmutableResult.of("validation.rulesets", validationRules);
    }

    @VisibleForTesting
    List<Result<ValidationReport>> executeRules(QueueEntry queueEntry, ImmutableFileReferences fileReferences, Set<ValidationRule> validationRules) {
        ImmutablePhaseData<ImmutableFileReferences> phaseData = ImmutablePhaseData.<ImmutableFileReferences>builder()
                .phase(queueHandlerService.reportPhase(uninitializedPhase(queueEntry.id(), EXECUTION_PHASE), PhaseState.START))
                .payload(fileReferences)
                .build();

        List<Result<ValidationReport>> results = validationRules.parallelStream()
                .map(this::findMatchingRule)
                .filter(Optional::isPresent)
                .map(r -> r.get().execute(phaseData))
                .map(CompletableFuture::join)
                .toList();
        // everything's done at this point because of the ::join call, complete phase and return
        queueHandlerService.reportPhase(phaseData.phase(), PhaseState.COMPLETE);
        return results;
    }

    private Optional<Rule> findMatchingRule(ValidationRule validationRule) {
        String identifyingName = validationRule.identifyingName();
        Optional<Rule> rule = Optional.ofNullable(rules.get(identifyingName));
        if (rule.isEmpty()) {
            LOGGER.error("No matching rule found with identifying name '{}' from available {}", identifyingName, rules.keySet());
        }
        return rule;
    }

}
