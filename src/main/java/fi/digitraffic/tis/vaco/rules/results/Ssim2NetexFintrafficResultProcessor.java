package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@Component
public class Ssim2NetexFintrafficResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<String> requiredFiles = Set.of("stderr.log", "stdout.log");
    private final TaskService taskService;

    public Ssim2NetexFintrafficResultProcessor(VacoProperties vacoProperties,
                                 PackagesService packagesService,
                                 S3Client s3Client,
                                 TaskService taskService,
                                 FindingService findingService,
                                 RulesetService rulesetService,
                                 ObjectMapper objectMapper){
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.taskService = taskService;
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        logger.info("Processing result from Ssim2Netex conversion for entry {}/task {}", entry.publicId(), task.name());
        ConcurrentMap<String, List<String>> packages = collectPackageContents(resultMessage.uploadedFiles());
        Set<String> filesFound = createOutputPackages(resultMessage, entry, task, requiredFiles);

        if (filesFound.isEmpty()) {
            if (!packages.containsKey("result") || packages.get("result").isEmpty()) {
                logger.warn("Entry {} task {} does not contain 'result' package.", resultMessage.entryId(), task.name());

                taskService.markStatus(entry, task, Status.FAILED);
                taskService.trackTask(entry, task, ProcessingState.COMPLETE);
                return false;
            }
        } else {
            requiredFilesNotFound(entry, task, filesFound);
            return false;
        }

        resolveTaskStatus(entry, task);
        return true;
    }
}
