package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import jakarta.validation.constraints.NotBlank;
import org.springframework.hateoas.RepresentationModel;

public class EntryResultResource extends RepresentationModel<EntryResultResource> {

    public EntryResultResource(String entryId) {
        this.entryId = entryId;
    }

    @NotBlank
    private final String entryId;

    public String getEntryId() {
        return entryId;
    }
}
