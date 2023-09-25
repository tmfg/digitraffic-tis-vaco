package fi.digitraffic.tis.vaco.rules.validation.gtfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
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
                                      PackagesService packagesService,
                                      MessagingService messagingService) {
        super("gtfs", rulesetRepository, errorHandlerService, s3Client, vacoProperties, packagesService, objectMapper, messagingService);
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
            List<ImmutableError> validationErrors = scanErrors(entry, task, reportFile);
            return ImmutableValidationReport.builder()
                    .message("Canonical GTFS validation report")
                    .addAllErrors(validationErrors)
                    .build();
        } else {
            // TODO: maybe a more descriptive message here?
            return ImmutableValidationReport.of("wh0t");
        }
    }

    private List<ImmutableError> scanErrors(Entry entry, Task task, Path reportFile) {
        try {
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            return report.notices()
                    .stream()
                    .flatMap(notice -> notice.sampleNotices()
                            .stream()
                            .map(sn -> ImmutableError.of(
                                    entry.id(),
                                    task.id(),
                                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                                    notice.code())
                                .withRaw(sn)))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), RULE_NAME, e);
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
