package fi.digitraffic.tis.vaco.rules.internal;

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
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Component
public class DownloadRule implements Rule<Entry, ResultMessage> {
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
            Task task = taskService.trackTask(taskService.findTask(entry.id(), DOWNLOAD_SUBTASK), ProcessingState.START);
            Path tempFilePath = TempFiles.getTaskTempFile(vacoProperties, entry, task, entry.format() + ".zip");

            /*
            Message(
                MessageId=70978bae-3b9a-4a78-93f8-4bf122624e50,
                ReceiptHandle=YWQxYjYwYzgtY2ZhNy00NGQyLTk1MGUtY2EwMmZmN2Y2NmM5IGFybjphd3M6c3FzOmV1LW5vcnRoLTE6MDAwMDAwMDAwMDAwOnJ1bGVzLXJlc3VsdHMgNzA5NzhiYWUtM2I5YS00YTc4LTkzZjgtNGJmMTIyNjI0ZTUwIDE2OTkyNjM1NjguOTM5MTAxNQ==,
                MD5OfBody=bf8cda13842ca81fa4b6b62a9631aa20,
                Body={
                    "entryId": "LPD3tLbTh5wOflaq4jumX",
                    "taskId": 1100023,
                    "ruleName": "gtfs.canonical.v4_1_0",
                    "inputs": "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/input",
                    "outputs": "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output",
                    "uploadedFiles": {
                        "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output/report.html": ["report", "all"],
                        "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output/system_errors.json": ["all"],
                        "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output/report.json": ["report", "all"],
                        "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output/stdout.log": ["debug", "all"],
                        "s3://digitraffic-tis-processing-local/entries/LPD3tLbTh5wOflaq4jumX/tasks/gtfs.canonical.v4_1_0/rules/gtfs.canonical.v4_1_0/output/stderr.log": ["debug", "all"]
                    }
                }
            )
             */

            try {
                // TODO: this is copypaste, refactor

                S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), DOWNLOAD_SUBTASK, DOWNLOAD_SUBTASK);
                S3Path ruleS3Input = ruleBasePath.resolve("input");
                S3Path ruleS3Output = ruleBasePath.resolve("output");

                S3Path result = httpClient.downloadFile(tempFilePath, entry.url(), entry.etag())
                    .thenApply(track(task, ProcessingState.UPDATE))
                    .thenCompose(uploadToS3(entry, ruleS3Output, task))
                    .thenApply(track(task, ProcessingState.COMPLETE))
                    .join();
                String downloadedFilePackage = "result";

                return ImmutableResultMessage.builder()
                    .entryId(entry.publicId())
                    .taskId(task.id())
                    .ruleName(DOWNLOAD_SUBTASK)
                    .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                    .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
                    .uploadedFiles(Map.of(result.asUri(vacoProperties.s3ProcessingBucket()), List.of(downloadedFilePackage)))
                    .build();
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
                                                                               S3Path outputDir,
                                                                               Task task) {
        return response -> {
            S3Path s3TargetPath = outputDir.resolve(entry.format() + ".zip");

            return s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), s3TargetPath, response.body())
                .thenApply(track(task, ProcessingState.UPDATE))
                .thenApply(u -> s3TargetPath);  // NOTE: There's probably something useful in the `u` parameter
        };
    }
}
