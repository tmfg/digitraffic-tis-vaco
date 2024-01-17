package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.summary.GtfsInputSummaryService;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class SummaryRule implements Rule<Entry, ResultMessage> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final String SUMMARY_TASK = "generate.summaries";
    private final TaskService taskService;
    private final PackagesService packagesService;
    private final GtfsInputSummaryService gtfsInputSummaryService;

    private final VacoProperties vacoProperties;

    public SummaryRule(TaskService taskService,
                       PackagesService packagesService,
                       GtfsInputSummaryService gtfsInputSummaryService,
                       VacoProperties vacoProperties) {
        this.taskService = Objects.requireNonNull(taskService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.gtfsInputSummaryService = Objects.requireNonNull(gtfsInputSummaryService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    @Override
    public String getIdentifyingName() {
        return SUMMARY_TASK;
    }

    @Override
    public CompletableFuture<ResultMessage> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Task> task = taskService.findTask(entry.id(), SUMMARY_TASK);

            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.START);
                Optional<Task> downloadTask = taskService.findTask(entry.id(), DownloadRule.DOWNLOAD_SUBTASK);
                if (downloadTask.isEmpty()) {
                    logger.error("Failed to generate task summaries due to entry's '{}' downloaded data task not existing in the db as a task", entry.id());
                    return unsuccessfulResult(t, entry);
                }

                Optional<Path> downloadedPackagePath =
                    packagesService.downloadPackage(entry, downloadTask.get(), "result");
                if (downloadedPackagePath.isEmpty()) {
                    logger.error("Failed to generate task summaries while trying to get S3 path to entry's '{}' downloaded data package", entry.id());
                    return unsuccessfulResult(t, entry);
                }

                taskService.trackTask(entry, t, ProcessingState.UPDATE);
                try {
                    if ("gtfs".equalsIgnoreCase(entry.format())) {
                        gtfsInputSummaryService.generateGtfsDownloadSummaries(downloadedPackagePath.get(), t.id());
                    } else if ("netex".equalsIgnoreCase(entry.format())) {
                    }
                } catch (Exception e) {
                    return unsuccessfulResult(t, entry);
                }

                S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(),
                    DownloadRule.DOWNLOAD_SUBTASK, DownloadRule.DOWNLOAD_SUBTASK);
                S3Path ruleS3Input = ruleBasePath.resolve("input");

                taskService.trackTask(entry, t, ProcessingState.COMPLETE);

                return ImmutableResultMessage.builder()
                    .entryId(entry.publicId())
                    .taskId(tracked.id())
                    .ruleName(SUMMARY_TASK)
                    .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                    .outputs("")
                    .uploadedFiles(new HashMap<>())
                    .build();
            }).orElseThrow();
        });
    }

    private ImmutableResultMessage unsuccessfulResult(Task t, Entry e) {
        taskService.trackTask(e, t, ProcessingState.COMPLETE);
        return ImmutableResultMessage.builder()
            .entryId(e.publicId())
            .taskId(t.id())
            .ruleName(SUMMARY_TASK)
            .inputs("")
            .outputs("")
            .uploadedFiles(new HashMap<>())
            .build();
    }
}
