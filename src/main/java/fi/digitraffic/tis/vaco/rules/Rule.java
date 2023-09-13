package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Rule<INPUT, OUTPUT> {
    String getIdentifyingName();

    CompletableFuture<OUTPUT> execute(
        Entry entry,
        Task task,
        S3Path inputDirectory,
        Optional<INPUT> configuration);
}
