package fi.digitraffic.tis.vaco.queue;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.ticket.TicketStatusView;
import fi.digitraffic.tis.vaco.validation.ValidationView;

import javax.validation.constraints.NotNull;

public record QueueEntryView(TicketStatusView status,
                             @NotNull InputMetadata input,
                             ValidationView validation,
                             ConversionView conversion) {
}
