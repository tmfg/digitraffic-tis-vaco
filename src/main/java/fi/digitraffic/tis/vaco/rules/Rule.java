package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;

import java.util.concurrent.CompletableFuture;

public interface Rule<INPUT, OUTPUT> {
    String getIdentifyingName();

    CompletableFuture<OUTPUT> execute(
        ValidationRuleJobMessage message);
}
