package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

@Component
public class InternalRuleResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final TaskService taskService;

    public InternalRuleResultProcessor(VacoProperties vacoProperties,
                                       PackagesService packagesService,
                                       S3Client s3Client,
                                       TaskService taskService,
                                       FindingService findingService,
                                       RulesetService rulesetService,
                                       ObjectMapper objectMapper) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        // use downloaded result file as is instead of repackaging the zip
        ConcurrentMap<String, List<String>> packages = collectPackageContents(resultMessage.uploadedFiles());
        if (!packages.containsKey("result") || packages.get("result").isEmpty()) {
            logger.warn("Entry {} internal task {} does not contain 'result' package.", resultMessage.entryId(), task.name());
            taskService.markStatus(entry, task, Status.FAILED);
            taskService.trackTask(entry, task, ProcessingState.COMPLETE);
            return false;
        } else {
            String sourceFile = packages.get("result").getFirst();
            S3Path dlFile = S3Path.of(URI.create(sourceFile).getPath());
            packagesService.registerPackage(ImmutablePackage.of(
                task,
                "result",
                dlFile.toString()));
            taskService.markStatus(entry, task, Status.SUCCESS);
            taskService.trackTask(entry, task, ProcessingState.COMPLETE);
            return true;
        }
    }
}
