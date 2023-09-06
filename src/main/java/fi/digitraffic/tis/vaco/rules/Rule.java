package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Rule<INPUT, OUTPUT> {
    String getIdentifyingName();

    CompletableFuture<OUTPUT> execute(
        Entry entry,
        Optional<INPUT> configuration,
        TaskData<FileReferences> taskData);
}
