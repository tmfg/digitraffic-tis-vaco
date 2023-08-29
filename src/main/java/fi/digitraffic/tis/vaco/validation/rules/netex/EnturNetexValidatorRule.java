package fi.digitraffic.tis.vaco.validation.rules.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.RuleExecutionException;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import fi.digitraffic.tis.vaco.validation.rules.ValidatorRule;
import org.entur.netex.validation.validator.NetexValidatorsRunner;
import org.entur.netex.validation.validator.schema.NetexSchemaValidator;
import org.entur.netex.validation.xml.NetexXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EnturNetexValidatorRule extends ValidatorRule implements Rule {
    public static final String RULE_NAME = "netex.entur.v1_0_1";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    public EnturNetexValidatorRule(
        RulesetRepository rulesetRepository,
        ErrorHandlerService errorHandlerService,
        ObjectMapper objectMapper) {
        super("netex", rulesetRepository, errorHandlerService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
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

        Path netexSource = taskData.payload().localPath();
        EnturNetexValidatorConfiguration conf = validateConfiguration(configuration);
        // TODO: send errors to reporting? or did that happen elsewhere?
        List<ImmutableError> validationErrors = validateNetex(conf, taskData, netexSource);
        return ImmutableValidationReport.builder()
            .message("Entur NeTEx validation report")
            .addAllErrors(validationErrors)
            .build();
    }

    private EnturNetexValidatorConfiguration validateConfiguration(
        Optional<ValidationInput> configuration) {
        return configuration.map(input -> {
            if (input.config() != null) {
                return (EnturNetexValidatorConfiguration) input.config();
            } else {
                return EnturNetexValidatorConfiguration.DEFAULTS;
            }
        }).orElse(EnturNetexValidatorConfiguration.DEFAULTS);
    }

    private List<ImmutableError> validateNetex(
        EnturNetexValidatorConfiguration configuration,
        TaskData<FileReferences> taskData,
        Path netexSource) {

        try (ZipFile zipFile = toZipFile(taskData, netexSource)) {
            return zipFile.stream()
                .filter(e -> !e.isDirectory())
                .map(zipEntry -> {
                    logger.debug("Extracting ZIP entry {} from archive...", zipEntry);
                    byte[] bytes = getEntryContents(taskData, zipFile, zipEntry);
                    return validateNetexEntry(configuration, zipEntry, bytes);
                }).flatMap(report -> {
                    return report.getValidationReportEntries().stream().map(e -> ImmutableError.of(
                        taskData.task().entryId(),
                        taskData.task().id(),
                        rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                        e.getMessage())
                    .withRaw(objectMapper.valueToTree(e)));
            }).toList();
        } catch (IOException e) {
            String message = "Failed to close ZIP stream " + netexSource + " gracefully";
            errorHandlerService.reportError(
                ImmutableError.of(
                    taskData.task().entryId(),
                    taskData.task().id(),
                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                    message));
            throw new RuleExecutionException(message, e);
        }
    }

    private ZipFile toZipFile(TaskData<FileReferences> taskData, Path netexSource) {
        ZipFile zipFile;
        try {
            logger.debug("Processing {} as ZIP file", netexSource);
            zipFile = new ZipFile(netexSource.toFile());
        } catch (IOException e1) {
            String message = "Failed to unzip provided NeTEx package " + netexSource;
            errorHandlerService.reportError(
                ImmutableError.of(
                    taskData.task().entryId(),
                    taskData.task().id(),
                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                    message));
            throw new RuleExecutionException(message, e1);
        }
        return zipFile;
    }

    private byte[] getEntryContents(TaskData<FileReferences> taskData, ZipFile zipFile, ZipEntry zipEntry) {
        byte[] bytes;
        try {
            bytes = zipFile.getInputStream(zipEntry).readAllBytes();
        } catch (IOException e) {
            String message = "Failed to access file " + zipEntry.getName() + " within provided NeTEx package " + zipFile.getName();
            errorHandlerService.reportError(
                ImmutableError.of(
                    taskData.task().entryId(),
                    taskData.task().id(),
                    rulesetRepository.findByName(RULE_NAME).orElseThrow().id(),
                    message));
            throw new RuleExecutionException(message, e);
        }
        return bytes;
    }

    private org.entur.netex.validation.validator.ValidationReport validateNetexEntry(EnturNetexValidatorConfiguration configuration, ZipEntry zipEntry, byte[] bytes) {
        logger.debug("Validating ZIP entry {}...", zipEntry);
        // TODO: accumulate max errors
        NetexXMLParser netexXMLParser = new NetexXMLParser(configuration.ignorableNetexElements());
        NetexSchemaValidator netexSchemaValidator = new NetexSchemaValidator(configuration.maximumErrors());
        NetexValidatorsRunner netexValidatorsRunner = new NetexValidatorsRunner(netexXMLParser, netexSchemaValidator, List.of());

        return netexValidatorsRunner.validate(
            configuration.codespace(),
            configuration.reportId(),
            zipEntry.getName(),
            bytes);
    }

}
