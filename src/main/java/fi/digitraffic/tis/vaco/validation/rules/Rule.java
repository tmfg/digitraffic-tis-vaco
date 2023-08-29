package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<ValidationReport> execute(
        Entry entry,
        Optional<ValidationInput> configuration,
        TaskData<FileReferences> taskData);
}
