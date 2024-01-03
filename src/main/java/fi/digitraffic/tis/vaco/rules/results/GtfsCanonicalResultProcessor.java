package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.ImmutableFinding;
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
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
public class GtfsCanonicalResultProcessor extends RuleResultProcessor implements ResultProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final S3Client s3Client;
    private final VacoProperties vacoProperties;
    private final RulesetService rulesetService;
    private final TaskService taskService;
    private final FindingService findingService;
    private final ObjectMapper objectMapper;

    public GtfsCanonicalResultProcessor(PackagesService packagesService,
                                        S3Client s3Client,
                                        VacoProperties vacoProperties,
                                        RulesetService rulesetService,
                                        TaskService taskService,
                                        FindingService findingService,
                                        ObjectMapper objectMapper) {
        super(packagesService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.taskService = Objects.requireNonNull(taskService);
        this.findingService = Objects.requireNonNull(findingService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        createOutputPackages(resultMessage, entry, task);

        Map<String, String> fileNames = collectOutputFileNames(resultMessage);

        // file specific handling
        if (fileNames.containsKey("report.json")) {
            Path ruleTemp = TempFiles.getRuleTempDirectory(vacoProperties, entry, task.name(), resultMessage.ruleName());
            Path outputDir = ruleTemp.resolve("output");

            Path reportFile = outputDir.resolve("report.json");
            URI s3Uri = URI.create(fileNames.get("report.json"));
            s3Client.downloadFile(s3Uri.getHost(), S3Path.of(s3Uri.getPath()), reportFile);

            List<Finding> findings = new ArrayList<>(scanReportFile(entry, task, resultMessage.ruleName(), reportFile));
            if (findings.isEmpty()) {
                taskService.markStatus(task, Status.SUCCESS);
            } else {
                findingService.reportFindings(findings);
                Map<String, Long> severities = findingService.summarizeFindingsSeverities(entry, task);
                logger.debug("{}/{} produced notices {}", entry.publicId(), task.name(), severities);
                if (severities.getOrDefault("ERROR", 0L) > 0) {
                    taskService.markStatus(task, Status.ERRORS);
                } else if (severities.getOrDefault("WARNING", 0L) > 0) {
                    taskService.markStatus(task, Status.WARNINGS);
                } else {
                    taskService.markStatus(task, Status.SUCCESS);
                }
            }
        } else {
            logger.warn("Expected file 'report.json' missing from output for message {}", resultMessage);
        }
        return true;
    }

    private List<ImmutableFinding> scanReportFile(Entry entry, Task task, String ruleName, Path reportFile) {
        try {
            Report report = objectMapper.readValue(reportFile.toFile(), Report.class);
            return report.notices()
                .stream()
                .flatMap(notice -> notice.sampleNotices()
                    .stream()
                    .map(sn -> {
                        try {
                            return ImmutableFinding.of(
                                    entry.publicId(),
                                    task.id(),
                                    rulesetService.findByName(ruleName)
                                        .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                                        .id(),
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

    /**
     * Truncate output filenames to contain only the file name without path. Assumes results are in flat directory.
     *
     * @param resultMessage Message to get the file names from.
     * @return Map of file names without directory prefixes to full file name.
     */
    private static Map<String, String> collectOutputFileNames(ResultMessage resultMessage) {
        return Streams.collect(
            resultMessage.uploadedFiles().keySet(),
            m -> m.substring(m.lastIndexOf('/') + 1),
            Function.identity());
    }
}
