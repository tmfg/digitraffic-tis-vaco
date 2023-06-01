package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<ValidationReport> execute();
}
