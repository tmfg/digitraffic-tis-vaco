package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
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

import java.util.Map;
import java.util.Set;

@Component
public class Ssim2NetexFintrafficResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<String> requiredFiles = Set.of("stderr.log", "stdout.log");

    public Ssim2NetexFintrafficResultProcessor(VacoProperties vacoProperties,
                                 PackagesService packagesService,
                                 S3Client s3Client,
                                 TaskService taskService,
                                 FindingService findingService,
                                 RulesetService rulesetService,
                                 ObjectMapper objectMapper){
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        logger.info("Processing result from PREPARE for entry {}/task {}", entry.publicId(), task.name());
        createOutputPackages(resultMessage, entry, task, requiredFiles);
        resolveTaskStatus(entry, task);
        return true;
    }
}
