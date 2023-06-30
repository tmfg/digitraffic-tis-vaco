package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<ValidationReport> execute(Entry queueEntry, PhaseData<FileReferences> phaseData);
}
