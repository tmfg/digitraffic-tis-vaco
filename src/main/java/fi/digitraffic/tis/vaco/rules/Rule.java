package fi.digitraffic.tis.vaco.rules;

import java.util.concurrent.CompletableFuture;

public interface Rule<I, O> {
    String getIdentifyingName();

    CompletableFuture<O> execute(I message);
}
