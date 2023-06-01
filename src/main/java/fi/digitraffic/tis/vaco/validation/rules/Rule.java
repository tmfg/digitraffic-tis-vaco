package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.validation.model.Result;

import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<Result> execute();
}
