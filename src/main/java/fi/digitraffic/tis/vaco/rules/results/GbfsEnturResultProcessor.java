package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import fi.digitraffic.tis.vaco.rules.model.gbfs.FileValidationResult;
import fi.digitraffic.tis.vaco.rules.model.gbfs.GbfsError;
import fi.digitraffic.tis.vaco.rules.model.gbfs.Report;
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

@Component
public class GbfsEnturResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final RulesetService rulesetService;
    private final Set<String> requiredFiles = new HashSet<>(Set.of("stderr.log", "stdout.log"));

    public GbfsEnturResultProcessor(VacoProperties vacoProperties,
                                    PackagesService packagesService,
                                    S3Client s3Client,
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

        logger.info("Processing result from {} for entry {}/task {}", RuleName.GBFS_ENTUR, entry.publicId(), task.name());
        Set<String> filesFound = createOutputPackages(resultMessage, entry, task, requiredFiles);

        if (filesFound.isEmpty()) {

            boolean reportsProcessed = processFile(resultMessage, entry, task, fileNames, "reports.json", reportsFile -> {
                List<Finding> findings = new ArrayList<>(scanReportsFile(entry, task, resultMessage.ruleName(), reportsFile));
                return storeFindings(findings);
            });

            resolveTaskStatus(entry, task);

            return reportsProcessed;

        } else {

            requiredFilesNotFound(entry, task, filesFound);
            return false;
        }

    }

    private List<Finding> scanReportsFile(Entry entry, Task task, String ruleName, Path reportsFile) {
        try {
            Long rulesetId = rulesetService.findByName(ruleName)
                .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                .id();
            List<Report> reports = objectMapper.readValue(reportsFile.toFile(), new TypeReference<>() {});
            return Streams.flatten(reports, report -> {
                    //report.errors(); // TODO: what is this?
                    FileValidationResult fileValidationResult = report.fileValidationResult();
                    List<GbfsError> errors = fileValidationResult.errors();
                    return Streams.collect(errors, error -> {
                        String trimmedMessage = error.message();
                        trimmedMessage = trimmedMessage.substring(trimmedMessage.indexOf(":") + 1).trim();
                        try {
                            return (Finding) ImmutableFinding.of(
                                    task.id(),
                                    rulesetId,
                                    fileValidationResult.file(),
                                    trimmedMessage,
                                    (fileValidationResult.required() ? "ERROR" : "WARNING"))
                                .withRaw(objectMapper.writeValueAsBytes(error));
                        } catch (JsonProcessingException e) {
                            logger.warn("Failed to convert tree to bytes", e);
                            return null;
                        }
                    });
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
    }
}
