package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.PhaseState;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.model.ImmutableResult;
import fi.digitraffic.tis.vaco.validation.model.Result;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import fi.digitraffic.tis.vaco.validation.repository.RuleSetsRepository;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
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

    private final VacoProperties vacoProperties;
    private final S3TransferManager s3TransferManager;
    private final QueueHandlerService queueHandlerService;
    private final HttpClient httpClient;
    private final RuleSetsRepository rulesetsRepository;
    private final Map<String, Rule> rules;

    public ValidationService(VacoProperties vacoProperties,
                             S3TransferManager s3TransferManager,
                             QueueHandlerService queueHandlerService,
                             HttpClient httpClient,
                             RuleSetsRepository rulesetsRepository,
                             List<Rule> rules) {
        this.vacoProperties = vacoProperties;
        this.s3TransferManager = s3TransferManager;
        this.queueHandlerService = queueHandlerService;
        this.httpClient = httpClient;
        this.rulesetsRepository = rulesetsRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
    }

    public ImmutableJobDescription validate(ImmutableJobDescription jobDescription) throws ValidationProcessException {
        Result<FileReferences> s3path = downloadFile(jobDescription);

        Result<Set<ValidationRule>> validationRules = selectRulesets(jobDescription);

        Result<List<ValidationReport>> results = executeRules(s3path.result(), validationRules.result());

        return jobDescription;
    }

    @VisibleForTesting
    Result<FileReferences> downloadFile(JobDescription jobDescription) {
        QueueEntry queueEntry = jobDescription.message();
        ImmutablePhase downloadPhase = queueHandlerService.reportPhase(
            queueEntry.id(),
            "validation.download",
            PhaseState.START);
        Path downloadFile = createDownloadTempFile(queueEntry);
        HttpRequest request = buildRequest(queueEntry);
        HttpResponse.BodyHandler<Path> bodyHandler = BodyHandlers.ofFile(downloadFile);
        return httpClient.sendAsync(request, bodyHandler)
            .thenCompose(uploadToS3(queueEntry))
            .thenApply(completeDownloadPhase(downloadPhase))
            .join(); // wait for finish - might be temporary
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

    private Function<HttpResponse<Path>, CompletableFuture<FileReferences>> uploadToS3(QueueEntry queueEntry) {
        return path -> {
            String s3Path = "entries/" + queueEntry.publicId() + "/download/" + queueEntry.format() + ".original";

            UploadFileRequest ufr = UploadFileRequest.builder()
                .putObjectRequest(req -> req.bucket(vacoProperties.getS3processingBucket()).key(s3Path))
                .addTransferListener(LoggingTransferListener.create())
                .source(path.body())
                .build();
            FileUpload upload = s3TransferManager.uploadFile(ufr);

            return upload.completionFuture()
                .thenApply(u -> new FileReferences(path.body(), s3Path, u));
        };
    }

    private Function<FileReferences, Result<FileReferences>> completeDownloadPhase(ImmutablePhase downloadPhase) {
        return uploadResult -> {
            LOGGER.info("S3 path: {}, upload status: {}", uploadResult.s3Path, uploadResult.upload);
            queueHandlerService.reportPhase(downloadPhase.entryId(), "validation.download", PhaseState.COMPLETE);
            return ImmutableResult.<FileReferences>builder().result(uploadResult).build();
        };
    }

    /**
     * Wrapper for getting reference to the downloaded file. Keeps both local file reference and S3 path reference in
     * case local file gets reaped before usage for any reason.
     */
    private class FileReferences {
        final Path localPath;
        final String s3Path;
        final CompletedFileUpload upload;

        public FileReferences(Path localPath, String s3Path, CompletedFileUpload upload) {
            this.localPath = localPath;
            this.s3Path = s3Path;
            this.upload = upload;
        }

    }

    @VisibleForTesting
    Result<Set<ValidationRule>> selectRulesets(ImmutableJobDescription jobDescription) {
        Set<ValidationRule> validationRules = rulesetsRepository.findRulesets(jobDescription.message().businessId());
        return ImmutableResult.of(validationRules);
    }

    @VisibleForTesting
    Result<List<ValidationReport>> executeRules(FileReferences fileReferences, Set<ValidationRule> validationRules) {
        return ImmutableResult.of(validationRules.parallelStream()
            .map(this::findMatchingRule)
            .filter(Optional::isPresent)
            .map(r -> r.get().execute())
            .map(CompletableFuture::join)
            .toList());
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
