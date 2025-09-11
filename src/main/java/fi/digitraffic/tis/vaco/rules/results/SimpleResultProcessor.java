package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of {@link ResultProcessor} to be used when rule specific implementation doesn't exist yet.
 */
@Component
public class SimpleResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final TaskService taskService;
    private final Set<String> requiredFiles = Set.of("stderr.log", "stdout.log");

    public SimpleResultProcessor(VacoProperties vacoProperties,
                                 PackagesService packagesService,
                                 S3Client s3Client,
                                 TaskService taskService,
                                 FindingService findingService,
                                 RulesetService rulesetService,
                                 ObjectMapper objectMapper){
        super(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, objectMapper);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @Override
    public boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        createOutputPackages(resultMessage, entry, task, requiredFiles);
        // this should call resolveTaskStatus(); if this logic gets _any_ more complex than this
        taskService.markStatus(entry, task, Status.SUCCESS);
        taskService.trackTask(entry, task, ProcessingState.COMPLETE);
        return true;
    }
}
