package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

@Component
public class NetexToGtfsRuleResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final TaskService taskService;
    private final S3Client s3Client;
    private final VacoProperties properties;

    public NetexToGtfsRuleResultProcessor(VacoProperties vacoProperties,
                                          PackagesService packagesService,
                                          S3Client s3Client,
                                          TaskService taskService,
                                          FindingService findingService) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.taskService = Objects.requireNonNull(taskService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.properties = Objects.requireNonNull(vacoProperties);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        // use downloaded result file as is instead of repackaging the zip
        ConcurrentMap<String, List<String>> packages = collectPackageContents(resultMessage.uploadedFiles());
        boolean resultFound;
        if (!packages.containsKey("result") || packages.get("result").isEmpty()) {
            logger.warn("Entry {} task {} does not contain 'result' package.", resultMessage.entryId(), task.name());
            taskService.markStatus(entry, task, Status.FAILED);
            taskService.trackTask(entry, task, ProcessingState.COMPLETE);
            resultFound = false;
        } else {
            String sourceFile = packages.get("result").getFirst();
            S3Path dlFile = S3Path.of(URI.create(sourceFile).getPath());
            S3Path resultPackagePath = ImmutableS3Path.of(List.of(entry.publicId(), Objects.requireNonNull(task.publicId()), dlFile.path().getLast()));
            s3Client.copyFile(properties.s3ProcessingBucket(), dlFile, properties.s3PackagesBucket(), resultPackagePath).join();
            packagesService.registerPackage(ImmutablePackage.of(
                    task,
                    "result",
                    resultPackagePath.toString()));
            taskService.markStatus(entry, task, Status.SUCCESS);
            taskService.trackTask(entry, task, ProcessingState.COMPLETE);
            resultFound = true;
        }
        packages.remove("result");

        packages.forEach((packageName, files) -> createOutputPackage(entry, task, packageName, files));

        return resultFound;

    }
}
