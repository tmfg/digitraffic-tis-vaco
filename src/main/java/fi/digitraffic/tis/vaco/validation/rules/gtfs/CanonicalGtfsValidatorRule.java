package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import fi.digitraffic.tis.vaco.validation.rules.ValidatorRule;
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

public class CanonicalGtfsValidatorRule extends ValidatorRule implements Rule {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String RULE_NAME = "gtfs.canonical.v4_0_0";

    private final ObjectMapper objectMapper;
    private final S3TransferManager s3TransferManager;
    private final VacoProperties vacoProperties;

    public CanonicalGtfsValidatorRule(
        ObjectMapper objectMapper,
        VacoProperties vacoProperties,
        S3TransferManager s3TransferManager,
        ErrorHandlerService errorHandlerService,
        RulesetRepository rulesetRepository) {
        super("gtfs", rulesetRepository, errorHandlerService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.s3TransferManager = Objects.requireNonNull(s3TransferManager);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    @Override
    public String getIdentifyingName() {
        return RULE_NAME;
    }

    @Override
    protected ValidationReport runValidator(
        Entry queueEntry,
        Optional<ValidationInput> configuration,
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
            // TODO: maybe a more descriptive message here?
            return ImmutableValidationReport.of("wh0t");
        }
    }

    private List<ImmutableError> scanErrors(Entry queueEntry, PhaseData<FileReferences> phaseData, Path reportFile) {
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
            logger.warn("Failed to process {}/{}/{} output file", queueEntry.publicId(), phaseData.phase().name(), RULE_NAME, e);
            return List.of();
        }
    }

    private List<ImmutableError> copyOutputToS3(Entry queueEntry, PhaseData<FileReferences> phaseData, Path outputDirectory) {
        String s3Path = S3Artifact.getValidationPhasePath(queueEntry.publicId(),
                                                          phaseData.phase().name(),
                                                          RULE_NAME);

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

    private static CountryCode resolveCountryCode(Entry queueEntry) {
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
