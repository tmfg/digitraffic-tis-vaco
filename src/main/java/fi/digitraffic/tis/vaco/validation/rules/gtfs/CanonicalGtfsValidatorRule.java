package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.PhaseData;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CanonicalGtfsValidatorRule implements Rule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanonicalGtfsValidatorRule.class);

    public static final String RULE_NAME = "gtfs.canonical.v4_0_0";

    private final S3TransferManager s3TransferManager;
    private final VacoProperties vacoProperties;

    public CanonicalGtfsValidatorRule(S3TransferManager s3TransferManager,
                                      VacoProperties vacoProperties) {
        this.s3TransferManager = s3TransferManager;
        this.vacoProperties = vacoProperties;
    }

    @Override
    public String getIdentifyingName() {
        return RULE_NAME;
    }

    @Override
    public CompletableFuture<ValidationReport> execute(QueueEntry queueEntry, PhaseData<FileReferences> phaseData) {
        return CompletableFuture.supplyAsync(() -> {
            if ("gtfs".equalsIgnoreCase(queueEntry.format())) {
                return runCanonicalValidator(queueEntry, phaseData);
            } else {
                return ImmutableValidationReport.of("what")
                        .withErrors(ImmutableError.of("Wrong format! Expected 'gtfs', was '" + queueEntry.format() + "'"));
            }
        });
    }

    private ValidationReport runCanonicalValidator(QueueEntry queueEntry, PhaseData<FileReferences> phaseData) {
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
            String s3Path = "entries/" + queueEntry.publicId() + "/phases/" + phaseData.phase().name() + "/" + RULE_NAME + "/output";

            CompletedDirectoryUpload ud = s3TransferManager.uploadDirectory(UploadDirectoryRequest.builder()
                            .bucket(vacoProperties.getS3processingBucket())
                            .s3Prefix(s3Path)
                            .source(outputDirectory)
                            .build())
                    .completionFuture()
                    .join();
            List<FailedFileUpload> failed = ud.failedTransfers();
            failed.forEach(failure -> LOGGER.warn(
                    "Failed to upload produced output file from {} to S3 {}:{}",
                    failure.request().source(),
                    failure.request().putObjectRequest().bucket(),
                    failure.request().putObjectRequest().key(),
                    failure.exception()));
            if (failed.size() == 0) {
                return ImmutableValidationReport.of("whut");
            } else {
                return ImmutableValidationReport.of("whet");
            }
        } else {
            return ImmutableValidationReport.of("wh0t");
        }
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
}
