package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
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
import fi.digitraffic.tis.vaco.rules.model.ssim.SsimReport;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.summary.SsimSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Component
public class  SsimFintrafficResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RulesetService rulesetService;
    private final ObjectMapper objectMapper;
    private final Set<String> requiredFiles = Set.of("stderr.log", "stdout.log");
    private final SsimSummaryService ssimSummaryService;

    public SsimFintrafficResultProcessor(VacoProperties vacoProperties,
                                         PackagesService packagesService,
                                         S3Client s3Client,
                                         TaskService taskService,
                                         FindingService findingService,
                                         RulesetService rulesetService,
                                         ObjectMapper objectMapper,
                                         SsimSummaryService summaryService){
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.ssimSummaryService = summaryService;
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        logger.info("Processing result from {} for entry {}/task {}", RuleName.SSIM_FINTRAFFIC, entry.publicId(), task.name());

        Set<String> filesFound = createOutputPackages(resultMessage, entry, task, requiredFiles);

        if (filesFound.isEmpty()) {
            boolean summaryProcessed = processFile(resultMessage, entry, task, fileNames, "summary.json", summaryPath -> {
                ssimSummaryService.generateSsimInputSummaries(entry, task, summaryPath);
                return true;
            });

            boolean reportProcessed = processFile(resultMessage, entry, task, fileNames, "report.json", reportFile -> {
                List<Finding> findings = new ArrayList<>(scanReportFile(entry, task, resultMessage.ruleName(), reportFile));
                return storeFindings(findings);
            });

            resolveTaskStatus(entry, task);

            return summaryProcessed && reportProcessed;
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
            SsimReport ssimReport = objectMapper.readValue(reportFile.toFile(), SsimReport.class);
            return ssimReport.findings()
                .stream()
                    .map(f -> {
                        try {
                            return ImmutableFinding.of(
                                    task.id(),
                                    rulesetId,
                                    ruleName,
                                    f.code(),
                                    f.severity())
                                .withRaw(objectMapper.writeValueAsBytes(f.context()));
                        } catch (JacksonException e) {
                            logger.warn("Failed to convert tree to bytes", e);
                            return null;
                        }
                    })
                .filter(Objects::nonNull)
                .toList();
        } catch (JacksonException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
    }
}
