package fi.digitraffic.tis.vaco.rules.conversion;

import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class ConverterRule implements Rule<ConversionInput, ValidationReport> {

    @Override
    public CompletableFuture<ValidationReport> execute(Entry entry, Optional<ConversionInput> configuration, TaskData<FileReferences> taskData) {
        return null;
    }
}
