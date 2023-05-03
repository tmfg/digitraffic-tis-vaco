package fi.digitraffic.tis.vaco.queue.entry;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.queue.InputMetadata;
import fi.digitraffic.tis.vaco.queue.steps.QueueEntryStatusView;
import fi.digitraffic.tis.vaco.validation.ValidationView;

import javax.validation.constraints.NotNull;

public record QueueEntryView(QueueEntryStatusView status,
                             @NotNull InputMetadata input,
                             ValidationView validation,
                             ConversionView conversion) {
}
