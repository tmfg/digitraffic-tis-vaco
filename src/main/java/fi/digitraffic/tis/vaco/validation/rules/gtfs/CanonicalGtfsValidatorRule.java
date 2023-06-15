package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.ruleset.RuleSetRepository;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.PhaseData;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.immutables.value.Value;
import org.mobilitydata.gtfsvalidator.input.CountryCode;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig;
import org.mobilitydata.gtfsvalidator.util.VersionInfo;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.FailedFileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CanonicalGtfsValidatorRule implements Rule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanonicalGtfsValidatorRule.class);

    public static final String RULE_NAME = "gtfs.canonical.v4_0_0";

    private final ObjectMapper objectMapper;
    private final S3TransferManager s3TransferManager;
    private final VacoProperties vacoProperties;
    private final ErrorHandlerService errorHandlerService;
    private final RuleSetRepository rulesetRepository;

    public CanonicalGtfsValidatorRule(
        ObjectMapper objectMapper,
        VacoProperties vacoProperties,
        S3TransferManager s3TransferManager,
        ErrorHandlerService errorHandlerService,
        RuleSetRepository rulesetRepository) {
        this.objectMapper = objectMapper;
        this.s3TransferManager = s3TransferManager;
        this.vacoProperties = vacoProperties;
        this.errorHandlerService = errorHandlerService;
        this.rulesetRepository = rulesetRepository;
    }

    @Override
    public String getIdentifyingName() {
        return RULE_NAME;
    }

    @Override
    public CompletableFuture<ValidationReport> execute(
        QueueEntry queueEntry,
        PhaseData<FileReferences> phaseData) {

        return CompletableFuture.supplyAsync(() -> {
            if ("gtfs".equalsIgnoreCase(queueEntry.format())) {
                return runCanonicalValidator(queueEntry, phaseData);
            } else {
                ImmutableError error = ImmutableError.of(
                    queueEntry.id(),
                    phaseData.phase().id(),
                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                    "Wrong format! Expected 'gtfs', was '%s'".formatted(queueEntry.format()));
                errorHandlerService.reportError(error);
                return ImmutableValidationReport.of("what").withErrors(error);
            }
        });
    }

    private ValidationReport runCanonicalValidator(
        QueueEntry queueEntry,
        PhaseData<FileReferences> phaseData) {

        Path ruleRoot = Path.of(vacoProperties.getTemporaryDirectory(), queueEntry.publicId(), phaseData.phase().name(), "rules", RULE_NAME);
        URI gtfsSource = phaseData.payload().localPath().toUri();
        Path storageDirectory = ruleRoot.resolve("storage");
        Path outputDirectory = ruleRoot.resolve("output");

        CountryCode countryCode = resolveCountryCode(queueEntry);

        ValidationRunnerConfig config = ValidationRunnerConfig.builder()
                .setCountryCode(countryCode)
                .setGtfsSource(gtfsSource)
                .setStorageDirectory(storageDirectory)
                .setOutputDirectory(outputDirectory)
                .setValidationReportFileName("report.json")
                .setPrettyJson(true)
                .build();
        new ValidationRunner(new StaticVersionResolver()).run(config);

        Path reportFile = outputDirectory.resolve("report.json");
        if (Files.exists(reportFile)) {
            List<ImmutableError> failedUploads = copyOutputToS3(queueEntry, phaseData, outputDirectory);
            List<ImmutableError> validationErrors = scanErrors(queueEntry, phaseData, reportFile);
            return ImmutableValidationReport.builder()
                    .message("Canonical GTFS validation report")
                    .addAllErrors(failedUploads)
                    .addAllErrors(validationErrors)
                    .build();
        } else {
            return ImmutableValidationReport.of("wh0t");
        }
    }

    private List<ImmutableError> scanErrors(QueueEntry queueEntry, PhaseData<FileReferences> phaseData, Path reportFile) {
        try {
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            List<ImmutableError> errors = report.notices()
                    .stream()
                    .flatMap(notice -> notice.sampleNotices()
                            .stream()
                            .map(sn -> ImmutableError.of(
                                            queueEntry.id(),
                                            phaseData.phase().id(),
                                            rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                                            notice.code())
                                    .withRaw(sn)))
                    .filter(Objects::nonNull)
                    .toList();
            errors.forEach(errorHandlerService::reportError);

            return errors;
        } catch (IOException e) {
            LOGGER.warn("Failed to process {}/{}/{} output file", queueEntry.publicId(), phaseData.phase().name(), RULE_NAME, e);
            return List.of();
        }
    }

    private List<ImmutableError> copyOutputToS3(QueueEntry queueEntry, PhaseData<FileReferences> phaseData, Path outputDirectory) {
        // TODO: some utility for generating these paths so that they stay consistent across entire app
        String s3Path = "entries/" + queueEntry.publicId() + "/phases/" + phaseData.phase().name() + "/" + RULE_NAME + "/output";

        CompletedDirectoryUpload ud = s3TransferManager.uploadDirectory(UploadDirectoryRequest.builder()
                        .bucket(vacoProperties.getS3processingBucket())
                        .s3Prefix(s3Path)
                        .source(outputDirectory)
                        .build())
                .completionFuture()
                .join();
        List<FailedFileUpload> failed = ud.failedTransfers();
        return failed.stream().map(failure -> {
            ImmutableError error = ImmutableError.of(
                    queueEntry.id(),
                    phaseData.phase().id(),
                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                    "Failed to upload produced output file from %s to S3 %s:%s".formatted(
                            failure.request().source(),
                            failure.request().putObjectRequest().bucket(),
                            failure.request().putObjectRequest().key()))
                    .withRaw(objectMapper.valueToTree(failure.exception()));
            errorHandlerService.reportError(error);
            return error;
        }).toList();
    }

    private static CountryCode resolveCountryCode(QueueEntry queueEntry) {
        if (queueEntry.metadata() != null && queueEntry.metadata().get("gtfs.countryCode") != null) {
            // TODO: document the gtfs.countryCode property somewhere
            return CountryCode.forStringOrUnknown(queueEntry.metadata().get("gtfs.countryCode").asText());
        } else {
            return CountryCode.forStringOrUnknown(CountryCode.ZZ);
        }
    }

    /**
     * Canonical GTFS validator contains a home calling version check component which is completely pointless for us. This
     * variant disables the feature by enforcing the current version to be the latest.
     */
    private static class StaticVersionResolver extends VersionResolver {
        @Override
        public VersionInfo getVersionInfoWithTimeout(Duration timeout) {
            return new VersionInfo() {
                @Override
                public Optional<String> currentVersion() {
                    return Optional.of("4.0.0");
                }

                @Override
                public Optional<String> latestReleaseVersion() {
                    return currentVersion();
                }

                @Override
                public boolean updateAvailable() {
                    return false;
                }
            };
        }

        @Override
        public synchronized void resolve() {
            // do nothing, on purpose
        }
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableReport.class)
    @JsonDeserialize(as = ImmutableReport.class)
    public interface Report {
        List<Notice> notices();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableNotice.class)
    @JsonDeserialize(as = ImmutableNotice.class)
    public interface Notice {
        String code();
        String severity();
        Long totalNotices();
        List<JsonNode> sampleNotices();
    }
}
