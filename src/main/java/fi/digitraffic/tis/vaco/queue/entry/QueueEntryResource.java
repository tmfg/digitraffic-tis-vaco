package fi.digitraffic.tis.vaco.queue.entry;

import org.springframework.hateoas.RepresentationModel;

import javax.validation.constraints.NotNull;

public class QueueEntryResource extends RepresentationModel<QueueEntryResource> {

    public QueueEntryResource(String ticketId) {
        this.ticketId = ticketId;
    }

    @NotNull
    private final String ticketId;

    public String getTicketId() {
        return ticketId;
    }
}
