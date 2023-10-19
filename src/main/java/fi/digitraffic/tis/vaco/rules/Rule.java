package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;

import java.util.concurrent.CompletableFuture;

public interface Rule<O> {
    String getIdentifyingName();

    CompletableFuture<O> execute(
        ValidationRuleJobMessage message);
}
