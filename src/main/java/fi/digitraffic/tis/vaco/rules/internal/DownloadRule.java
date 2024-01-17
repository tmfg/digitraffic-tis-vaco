package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class DownloadRule implements Rule<Entry, ResultMessage> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final String DOWNLOAD_SUBTASK = "prepare.download";
    private final TaskService taskService;
    private final VacoProperties vacoProperties;
    private final HttpClient httpClient;
    private final S3Client s3Client;

    public DownloadRule(TaskService taskService,
                        VacoProperties vacoProperties,
                        HttpClient httpClient,
                        S3Client s3Client) {
        this.taskService = Objects.requireNonNull(taskService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.s3Client = Objects.requireNonNull(s3Client);
    }

    @Override
    public String getIdentifyingName() {
        return DOWNLOAD_SUBTASK;
    }

    @Override
    public CompletableFuture<ResultMessage> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Task> task = taskService.findTask(entry.id(), DOWNLOAD_SUBTASK);
            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.START);
                Path tempFilePath = TempFiles.getTaskTempFile(vacoProperties, entry, tracked, entry.format() + ".zip");

                try {
                    S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), DOWNLOAD_SUBTASK, DOWNLOAD_SUBTASK);
                    S3Path ruleS3Input = ruleBasePath.resolve("input");
                    S3Path ruleS3Output = ruleBasePath.resolve("output");

                    S3Path result = httpClient.downloadFile(tempFilePath, entry.url(), entry.etag())
                        .thenApply(track(entry, tracked, ProcessingState.UPDATE))
                        .thenCompose(uploadToS3(entry, ruleS3Output, tracked))
                        .thenApply(track(entry, tracked, ProcessingState.COMPLETE))
                        .thenApply(status(entry, tracked, Status.SUCCESS))
                        .join();
                    String downloadedFilePackage = "result";

                    return ImmutableResultMessage.builder()
                        .entryId(entry.publicId())
                        .taskId(tracked.id())
                        .ruleName(DOWNLOAD_SUBTASK)
                        .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                        .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
                        .uploadedFiles(Map.of(result.asUri(vacoProperties.s3ProcessingBucket()), List.of(downloadedFilePackage)))
                        .build();
                }  catch (Exception e) {
                    logger.warn("Caught unrecoverable exception during file download", e);
                    taskService.markStatus(entry, tracked, Status.FAILED);
                    return null;
                } finally {
                    try {
                        Files.deleteIfExists(tempFilePath);
                    } catch (IOException ignored) {
                        // NOTE: ignored exception on purpose, although we could re-throw
                    }
                }
            }).orElseThrow();
        });
    }

    private <T> Function<T, T> track(Entry entry, Task task, ProcessingState state) {
        return t -> {
            taskService.trackTask(entry, task, state);
            return t;
        };
    }

    private <T> Function<T, T> status(Entry entry, Task task, Status status) {
        return t -> {
            taskService.markStatus(entry, task, status);
            return t;
        };
    }

    private Function<Path, CompletableFuture<S3Path>> uploadToS3(Entry entry,
                                                                 S3Path outputDir,
                                                                 Task task) {
        return path -> {
            S3Path s3TargetPath = outputDir.resolve(entry.format() + ".zip");

            return s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), s3TargetPath, path)
                .thenApply(track(entry, task, ProcessingState.UPDATE))
                .thenApply(u -> s3TargetPath);  // NOTE: There's probably something useful in the `u` parameter
        };
    }
}
