package fi.digitraffic.tis.vaco.rules.conversion;

import fi.digitraffic.tis.vaco.conversion.model.ConversionReport;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;

import java.util.concurrent.CompletableFuture;

public abstract class ConverterRule implements Rule<ValidationRuleJobMessage, ConversionReport> {
    @Override
    public CompletableFuture<ConversionReport> execute(
        ValidationRuleJobMessage message) {
        return null;
    }
}
