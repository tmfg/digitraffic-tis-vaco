package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

@Component
public class InternalRuleResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final TaskService taskService;

    public InternalRuleResultProcessor(VacoProperties vacoProperties, PackagesService packagesService, S3Client s3Client, TaskService taskService, FindingService findingService) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        // use downloaded result file as is instead of repackaging the zip
        ConcurrentMap<String, List<String>> packages = collectPackageContents(resultMessage.uploadedFiles());
        if (!packages.containsKey("result") || packages.get("result").isEmpty()) {
            throw new RuleExecutionException("Entry " + resultMessage.entryId() + " internal task " + task.name() + " does not contain 'result' package.");
        }
        String sourceFile = packages.get("result").get(0);
        S3Path dlFile = S3Path.of(URI.create(sourceFile).getPath());
        packagesService.registerPackage(ImmutablePackage.of(
            task.id(),
            "result",
            dlFile.toString()));
        taskService.markStatus(task, Status.SUCCESS);
        return true;
    }
}
