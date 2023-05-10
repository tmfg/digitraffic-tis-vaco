package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import org.springframework.hateoas.RepresentationModel;

import javax.validation.constraints.NotNull;

public class EntryResultResource extends RepresentationModel<EntryResultResource> {

    public EntryResultResource(String entryId) {
        this.entryId = entryId;
    }

    @NotNull
    private final String entryId;

    public String getEntryId() {
        return entryId;
    }
}
