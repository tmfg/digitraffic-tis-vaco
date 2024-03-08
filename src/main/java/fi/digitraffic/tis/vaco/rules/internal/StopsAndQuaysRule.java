package fi.digitraffic.tis.vaco.rules.internal;

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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Serve the static Stops and Quays file from app resources.
 */
@Component
public class StopsAndQuaysRule implements Rule<Entry, ResultMessage> {
    public static final String STOPS_AND_QUAYS_TASK = "prepare.stopsAndQuays";
    private final TaskService taskService;
    private final VacoProperties vacoProperties;
    private final S3Client s3Client;

    public StopsAndQuaysRule(TaskService taskService,
                             VacoProperties vacoProperties,
                             S3Client s3Client) {
        this.taskService = Objects.requireNonNull(taskService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.s3Client = Objects.requireNonNull(s3Client);
    }

    @Override
    public String getIdentifyingName() {
        return STOPS_AND_QUAYS_TASK;
    }

    @Override
    public CompletableFuture<ResultMessage> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Task> task = taskService.findTask(entry.publicId(), STOPS_AND_QUAYS_TASK);
            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.START);

                try {
                    S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), STOPS_AND_QUAYS_TASK, STOPS_AND_QUAYS_TASK);
                    S3Path ruleS3Input = ruleBasePath.resolve("input");
                    S3Path ruleS3Output = ruleBasePath.resolve("output");

                    Path stopsAndQuays = Path.of(Thread.currentThread().getContextClassLoader().getResource("private/static/emptyStopsAndQuays.zip").toURI());
                    S3Path target = ruleS3Output.resolve("stopsAndQuays.zip");

                    s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), target, stopsAndQuays).join();

                    taskService.trackTask(entry, t, ProcessingState.COMPLETE);

                    return ImmutableResultMessage.builder()
                        .entryId(entry.publicId())
                        .taskId(tracked.id())
                        .ruleName(STOPS_AND_QUAYS_TASK)
                        .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                        .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
                        .uploadedFiles(Map.of(target.asUri(vacoProperties.s3ProcessingBucket()), List.of("result")))
                        .build();
                } catch (URISyntaxException e) {
                    // thrown if static file is unavailable
                    throw new RuleExecutionException("Static file 'stopsAndQuays.zip' unavailable", e);
                }
            }).orElseThrow();
        });
    }
}
