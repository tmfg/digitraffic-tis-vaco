package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.gtfs.Report;
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
import java.util.Optional;
import java.util.Set;

@Component
public class GtfsCanonicalResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FindingService findingService;
    private final RulesetService rulesetService;
    private final ObjectMapper objectMapper;
    private final Set<String> requiredFiles = new HashSet<>(Set.of("stderr.log", "stdout.log"));

    public GtfsCanonicalResultProcessor(VacoProperties vacoProperties,
                                        PackagesService packagesService,
                                        S3Client s3Client,
                                        TaskService taskService,
                                        FindingService findingService,
                                        RulesetService rulesetService,
                                        ObjectMapper objectMapper) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.findingService = Objects.requireNonNull(findingService);
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
logger.info("Processing result from {} for entry {}/task {}", RuleName.GTFS_CANONICAL, entry.publicId(), task.name());
        Set<String> filesFound = createOutputPackages(resultMessage, entry, task, requiredFiles);

        if (filesFound.isEmpty()) {

            // file specific handling
            boolean reportProcessed = processFile(resultMessage, entry, task, fileNames, "report.json", reportFile -> {
                List<Finding> findings = new ArrayList<>(scanReportFile(entry, task, resultMessage.ruleName(), reportFile));
                return storeFindings(findings);
            });

            boolean errorsProcessed = processFile(resultMessage, entry, task, fileNames, "system_errors.json", errorFile -> {
                List<Finding> findings = new ArrayList<>(scanReportFile(entry, task, resultMessage.ruleName(), errorFile));
                return storeFindings(findings);
            });

            List<Finding> allFindings = findingService.findFindingsByName(task, "thread_execution_error");
            Optional<Status> status;
            if (allFindings.isEmpty()) {
                status = Optional.empty();
            } else {
                status = Optional.of(Status.FAILED);
            }
            resolveTaskStatus(entry, task, status);

            return reportProcessed && errorsProcessed;

        } else {

            requiredFilesNotFound(entry, task, filesFound);
            return false;
        }
    }

    private List<ImmutableFinding> scanReportFile(Entry entry, Task task, String ruleName, Path reportFile) {
        try {
            Long rulesetId = rulesetService.findByName(ruleName)
                .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                .id();
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            return report.notices()
                .stream()
                .flatMap(notice -> notice.sampleNotices()
                    .stream()
                    .map(sn -> {
                        try {
                            return ImmutableFinding.of(
                                    task.id(),
                                    rulesetId,
                                    ruleName,
                                    notice.code(),
                                    notice.severity())
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
}
