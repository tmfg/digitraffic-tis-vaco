package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class DownloadRule implements Rule<Entry, S3Path> {
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
    public CompletableFuture<S3Path> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Task task = taskService.trackTask(taskService.findTask(entry.id(), DOWNLOAD_SUBTASK), ProcessingState.START);
            Path tempFilePath = TempFiles.getTaskTempFile(vacoProperties, entry, task, entry.format() + ".zip");

            try {
                return httpClient.downloadFile(tempFilePath, entry.url(), entry.etag())
                    .thenApply(track(task, ProcessingState.UPDATE))
                    .thenCompose(uploadToS3(entry, task))
                    .thenApply(track(task, ProcessingState.COMPLETE))
                    .join();
            } finally {
                try {
                    Files.deleteIfExists(tempFilePath);
                } catch (IOException ignored) {
                    // NOTE: ignored exception on purpose, although we could re-throw
                }
            }

        });
    }

    private <T> Function<T, T> track(Task task, ProcessingState state) {
        return t -> {
            taskService.trackTask(task, state);
            return t;
        };
    }

    private Function<HttpResponse<Path>, CompletableFuture<S3Path>> uploadToS3(Entry entry,
                                                                               Task task) {
        return response -> {
            ImmutableFileReferences refs = ImmutableFileReferences.of(response.body());
            S3Path s3TargetPath = ImmutableS3Path.builder()
                .from(S3Artifact.getTaskPath(entry.publicId(), DOWNLOAD_SUBTASK))
                .addPath(entry.format() + ".zip")
                .build();

            return s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), s3TargetPath, refs.localPath())
                .thenApply(track(task, ProcessingState.UPDATE))
                .thenApply(u -> s3TargetPath);  // NOTE: There's probably something useful in the `u` parameter
        };
    }
}
