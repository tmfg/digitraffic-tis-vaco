package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.netex.ValidationResult;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NetexEnturValidatorResultProcessor extends RuleResultProcessor implements ResultProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final RulesetService rulesetService;
    private static final Pattern pattern = Pattern.compile("^(cvc-[a-zA-Z-]+(?:\\.[0-9a-zA-Z]+)+):.*");
    private final Set<String> requiredFiles = Set.of("stderr.log", "stdout.log");

    protected NetexEnturValidatorResultProcessor(PackagesService packagesService,
                                                 S3Client s3Client,
                                                 VacoProperties vacoProperties,
                                                 TaskService taskService,
                                                 FindingService findingService,
                                                 ObjectMapper objectMapper,
                                                 RulesetService rulesetService) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.rulesetService = Objects.requireNonNull(rulesetService);
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        logger.info("Processing result from {} for entry {}/task {}", RuleName.NETEX_ENTUR, entry.publicId(), task.name());
        Set<String> filesFound = createOutputPackages(resultMessage, entry, task, requiredFiles);

        if (filesFound.isEmpty()) {

            boolean reportsProcessed = processFile(resultMessage, entry, task, fileNames, "reports.json", path -> {
                List<Finding> findings = new ArrayList<>(scanReportsFile(entry, task, resultMessage.ruleName(), path));
                return storeFindings(findings);
            });

            resolveTaskStatus(entry, task);

            return reportsProcessed;

        } else {

            requiredFilesNotFound(entry, task, filesFound);
            return false;
        }

    }

    private List<ImmutableFinding> scanReportsFile(Entry entry, Task task, String ruleName, Path reportsFile) {

        try {
            List<ValidationResult> validationReport = objectMapper.readValue(reportsFile.toFile(), objectMapper.getTypeFactory().constructCollectionType(List.class, ValidationResult.class));
            return Streams.flatten(validationReport, report -> report.validationReport().validationReportEntries())
                    .map(reportEntry -> {
                        String message =  reportEntry.message();
                        Matcher matcher = pattern.matcher(message);
                        if (matcher.find()) {
                            message = matcher.group(1);
                        }
                        try {
                            return ImmutableFinding.of(
                                    task.id(),
                                    rulesetService.findByName(ruleName)
                                        .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                                        .id(),
                                    ruleName,
                                    message,
                                    reportEntry.severity())
                                .withRaw(objectMapper.writeValueAsBytes(reportEntry));
                        } catch (JsonProcessingException e) {
                            logger.warn("Failed to convert tree to bytes", e);
                            return null;
                        }
                    })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
        }
        return List.of();
    }

}
