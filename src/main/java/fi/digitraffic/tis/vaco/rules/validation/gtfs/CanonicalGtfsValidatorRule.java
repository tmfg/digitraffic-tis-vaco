package fi.digitraffic.tis.vaco.rules.validation.gtfs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.mobilitydata.gtfsvalidator.input.CountryCode;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig;
import org.mobilitydata.gtfsvalidator.util.VersionInfo;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final ObjectMapper objectMapper;

    public CanonicalGtfsValidatorRule(ObjectMapper objectMapper,
                                      VacoProperties vacoProperties,
                                      ErrorHandlerService errorHandlerService,
                                      RulesetRepository rulesetRepository,
                                      S3Client s3Client,
                                      PackagesService packagesService,
                                      TaskService taskService) {
        super("gtfs", rulesetRepository, errorHandlerService, s3Client, vacoProperties, packagesService, taskService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public String getIdentifyingName() {
        return RuleName.GTFS_CANONICAL_4_0_0;
    }

    @Override
    protected ValidationReport runValidator(Entry entry,
                                            Task task,
                                            Path workDirectory,
                                            Path outputsDirectory,
                                            Optional<ValidationInput> configuration) {
        URI gtfsSource = workDirectory.resolve(entry.format() + ".zip").toUri();

        CountryCode countryCode = resolveCountryCode(entry);

        ValidationRunnerConfig config = ValidationRunnerConfig.builder()
                .setCountryCode(countryCode)
                .setGtfsSource(gtfsSource)
                .setOutputDirectory(outputsDirectory)
                .setValidationReportFileName("report.json")
                .setPrettyJson(true)
                .build();
        new ValidationRunner(new StaticVersionResolver()).run(config);

        Path reportFile = outputsDirectory.resolve("report.json");
        if (Files.exists(reportFile)) {
            List<ImmutableError> validationErrors = scanReportFile(entry, task, RuleName.GTFS_CANONICAL_4_0_0, reportFile);
            return ImmutableValidationReport.builder()
                    .message("Canonical GTFS validation report")
                    .addAllErrors(validationErrors)
                    .build();
        } else {
            // TODO: maybe a more descriptive message here?
            return ImmutableValidationReport.of("wh0t");
        }
    }

    public List<ImmutableError> scanReportFile(Entry entry, Task task, String ruleName, Path reportFile) {
        try {
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            return report.notices()
                    .stream()
                    .flatMap(notice -> notice.sampleNotices()
                            .stream()
                            .map(sn -> {
                                try {
                                    return ImmutableError.of(
                                            entry.publicId(),
                                            task.id(),
                                            rulesetRepository.findByName(ruleName).orElseThrow().id(),
                                            ruleName,
                                            notice.code())
                                        .withRaw(objectMapper.writeValueAsBytes(sn));
                                } catch (JsonProcessingException e) {
                                    logger.warn("Failed to convert tree to bytes", e);
                                    return null;
                                }
                            }))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
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

}
