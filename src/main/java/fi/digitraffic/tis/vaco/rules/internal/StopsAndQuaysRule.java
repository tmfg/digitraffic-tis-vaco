package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Serve the Stops and Quays file from the dynamic cache maintained by {@link StopsAndQuaysCacheService}.
 */
@Component
public class StopsAndQuaysRule implements Rule<Entry, ResultMessage> {
    public static final String PREPARE_STOPS_AND_QUAYS_TASK = "prepare.stopsAndQuays";
    private final TaskService taskService;
    private final VacoProperties vacoProperties;
    private final S3Client s3Client;
    private final StopsAndQuaysCacheService stopsAndQuaysCacheService;

    public StopsAndQuaysRule(TaskService taskService,
                             VacoProperties vacoProperties,
                             S3Client s3Client,
                             StopsAndQuaysCacheService stopsAndQuaysCacheService) {
        this.taskService = Objects.requireNonNull(taskService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.stopsAndQuaysCacheService = Objects.requireNonNull(stopsAndQuaysCacheService);
    }

    @Override
    public CompletableFuture<ResultMessage> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Task> task = taskService.findTask(entry.publicId(), PREPARE_STOPS_AND_QUAYS_TASK);
            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.START);

                S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), PREPARE_STOPS_AND_QUAYS_TASK, PREPARE_STOPS_AND_QUAYS_TASK);
                S3Path ruleS3Input = ruleBasePath.resolve("input");
                S3Path ruleS3Output = ruleBasePath.resolve("output");

                Path stopsAndQuays = stopsAndQuaysCacheService.currentCacheFile()
                    .orElseThrow(() -> new RuleExecutionException("Stops and quays data not yet available in cache"));

                S3Path s3TargetPath = ImmutableS3Path.of(List.of(entry.publicId(), Objects.requireNonNull(t.publicId()),"stopsAndQuays.zip"));

                s3Client.uploadFile(vacoProperties.s3PackagesBucket(), s3TargetPath, stopsAndQuays).join();

                return ImmutableResultMessage.builder()
                    .entryId(entry.publicId())
                    .taskId(tracked.id())
                    .ruleName(PREPARE_STOPS_AND_QUAYS_TASK)
                    .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                    .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
                    .uploadedFiles(Map.of(s3TargetPath.asUri(vacoProperties.s3PackagesBucket()), List.of("result")))
                    .build();
            }).orElseThrow();
        });
    }
}
