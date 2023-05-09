package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import org.springframework.hateoas.RepresentationModel;

import javax.validation.constraints.NotNull;

public class EntryResource extends RepresentationModel<EntryResource> {

    public EntryResource(String entryId) {
        this.entryId = entryId;
    }

    @NotNull
    private final String entryId;

    public String getEntryId() {
        return entryId;
    }
}
