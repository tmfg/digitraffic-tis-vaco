package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.PhaseState;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.model.RuleSet;
import fi.digitraffic.tis.vaco.validation.repository.RulesetsRepository;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Service
public class ValidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    private final VacoProperties vacoProperties;
    private final S3TransferManager s3TransferManager;
    private final QueueHandlerService queueHandlerService;
    private final HttpClient httpClient;
    private final RulesetsRepository rulesetsRepository;

    public ValidationService(VacoProperties vacoProperties,
                             S3TransferManager s3TransferManager,
                             QueueHandlerService queueHandlerService,
                             HttpClient httpClient,
                             RulesetsRepository rulesetsRepository) {
        this.vacoProperties = vacoProperties;
        this.s3TransferManager = s3TransferManager;
        this.queueHandlerService = queueHandlerService;
        this.httpClient = httpClient;
        this.rulesetsRepository = rulesetsRepository;
    }

    public ImmutableJobDescription validate(ImmutableJobDescription jobDescription) throws ValidationProcessException {
        String s3path = downloadFile(jobDescription);

        Set<RuleSet> ruleSets = selectRulesets(jobDescription);

        return jobDescription;
    }

    private String downloadFile(JobDescription jobDescription) {
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
                .thenApply(cleanDownload(downloadFile, downloadPhase))
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

    private Function<HttpResponse<Path>, CompletionStage<S3UploadResult>> uploadToS3(QueueEntry queueEntry) {
        return path -> {
            String s3Path = "entries/" + queueEntry.publicId() + "/download/" + queueEntry.format() + ".original";

            UploadFileRequest ufr = UploadFileRequest.builder()
                    .putObjectRequest(req -> req.bucket(vacoProperties.getS3processingBucket()).key(s3Path))
                    .addTransferListener(LoggingTransferListener.create())
                    .source(path.body())
                    .build();
            FileUpload upload = s3TransferManager.uploadFile(ufr);
            return CompletableFuture.supplyAsync(() -> s3Path)
                    .thenCombine(upload.completionFuture(), S3UploadResult::new);
        };
    }

    private Function<S3UploadResult, String> cleanDownload(Path downloadFile, ImmutablePhase downloadPhase) {
        return upload -> {
            LOGGER.info("S3 path: {}, upload status: {}", upload.path, upload.upload);
            try {
                if (Files.exists(downloadFile)) {
                    Files.delete(downloadFile);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not delete temporary file {}", downloadFile, e);
            }
            queueHandlerService.reportPhase(downloadPhase.entryId(), "validation.download", PhaseState.COMPLETE);
            return upload.path;
        };
    }

    private class S3UploadResult {

        final String path;
        final CompletedFileUpload upload;
        public S3UploadResult(String path, CompletedFileUpload upload) {
            this.path = path;
            this.upload = upload;
        }

    }
    private Set<RuleSet> selectRulesets(ImmutableJobDescription jobDescription) {
        Set<RuleSet> ruleSets = rulesetsRepository.findRulesets(jobDescription.message().businessId());
        return ruleSets;
    }
}
