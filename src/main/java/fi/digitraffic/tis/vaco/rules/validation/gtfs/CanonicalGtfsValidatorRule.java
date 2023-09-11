package fi.digitraffic.tis.vaco.rules.validation.gtfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.immutables.value.Value;
import org.mobilitydata.gtfsvalidator.input.CountryCode;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig;
import org.mobilitydata.gtfsvalidator.util.VersionInfo;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CanonicalGtfsValidatorRule extends ValidatorRule {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String RULE_NAME = "gtfs.canonical.v4_0_0";

    private final ObjectMapper objectMapper;
    private final VacoProperties vacoProperties;
    private final S3Client s3Client;
    private final PackagesService packagesService;

    public CanonicalGtfsValidatorRule(ObjectMapper objectMapper,
                                      VacoProperties vacoProperties,
                                      ErrorHandlerService errorHandlerService,
                                      RulesetRepository rulesetRepository,
                                      S3Client s3Client,
                                      PackagesService packagesService) {
        super("gtfs", rulesetRepository, errorHandlerService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.packagesService = Objects.requireNonNull(packagesService);
    }

    @Override
    public String getIdentifyingName() {
        return RULE_NAME;
    }

    @Override
    protected ValidationReport runValidator(
        Entry queueEntry,
        Optional<ValidationInput> configuration,
        TaskData<FileReferences> taskData) {

        Path ruleRoot = TempFiles.getRuleTempDirectory(vacoProperties, queueEntry.publicId(), taskData.task().name(), RULE_NAME);

        URI gtfsSource = taskData.payload().localPath().toUri();
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
            List<ImmutableError> failedUploads = copyOutputToS3(queueEntry, taskData, outputDirectory);
            List<ImmutableError> validationErrors = scanErrors(queueEntry, taskData, reportFile);
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

    private List<ImmutableError> scanErrors(Entry queueEntry, TaskData<FileReferences> taskData, Path reportFile) {
        try {
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            List<ImmutableError> errors = report.notices()
                    .stream()
                    .flatMap(notice -> notice.sampleNotices()
                            .stream()
                            .map(sn -> ImmutableError.of(
                                            queueEntry.id(),
                                            taskData.task().id(),
                                            rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                                            notice.code())
                                    .withRaw(sn)))
                    .filter(Objects::nonNull)
                    .toList();
            errors.forEach(errorHandlerService::reportError);

            return errors;
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", queueEntry.publicId(), taskData.task().name(), RULE_NAME, e);
            return List.of();
        }
    }

    private List<ImmutableError> copyOutputToS3(Entry entry, TaskData<FileReferences> taskData, Path outputDirectory) {
        // copy produced output to S3
        String s3TargetPath = S3Artifact.getRuleDirectory(
                entry.publicId(),
                taskData.task().name(),
                RULE_NAME);
        CompletedDirectoryUpload ud = s3Client.uploadDirectory(
                outputDirectory,
                vacoProperties.getS3ProcessingBucket(),
                s3TargetPath)
            .join();
        // package and publish all of it
        packagesService.createPackage(entry, taskData.task(), RULE_NAME, s3TargetPath, "content.zip");

        // record failures if any
        return Streams.map(ud.failedTransfers(), failure -> {
            ImmutableError error = ImmutableError.of(
                    entry.id(),
                    taskData.task().id(),
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
