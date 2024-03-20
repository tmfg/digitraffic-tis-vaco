package fi.digitraffic.tis.vaco.rules;

import java.util.concurrent.CompletableFuture;

public interface Rule<I, O> {
    CompletableFuture<O> execute(I message);
}
