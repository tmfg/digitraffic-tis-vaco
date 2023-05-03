package fi.digitraffic.tis.vaco.queue.entry;

import org.springframework.hateoas.RepresentationModel;

import javax.validation.constraints.NotNull;

public class QueueEntryResource extends RepresentationModel<QueueEntryResource> {

    public QueueEntryResource(String entryId) {
        this.entryId = entryId;
    }

    @NotNull
    private final String entryId;

    public String getEntryId() {
        return entryId;
    }
}
