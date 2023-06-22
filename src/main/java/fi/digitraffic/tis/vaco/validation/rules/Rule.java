package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<ValidationReport> execute(QueueEntry queueEntry, PhaseData<FileReferences> phaseData);
}
