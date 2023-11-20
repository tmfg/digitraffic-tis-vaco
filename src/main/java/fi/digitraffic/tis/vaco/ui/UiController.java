package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import fi.digitraffic.tis.vaco.ui.model.ImmutableEntryState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/ui")
public class UiController {

    private final VacoProperties vacoProperties;

    private final EntryStateService entryStateService;

    private final QueueHandlerService queueHandlerService;

    public UiController(VacoProperties vacoProperties, EntryStateService entryStateService, QueueHandlerService queueHandlerService) {
        this.vacoProperties = vacoProperties;
        this.entryStateService = entryStateService;
        this.queueHandlerService = queueHandlerService;
    }

    @GetMapping(path = "/bootstrap")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableBootstrap> bootstrap() {
        return ResponseEntity.ok()
            .body(ImmutableBootstrap.of(
                vacoProperties.environment(),
                vacoProperties.baseUrl(),
                vacoProperties.azureAd().tenantId(),
                vacoProperties.azureAd().clientId()));
    }

    @GetMapping(path = "/entry/{publicId}/state")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableEntryState>> fetchEntryState(@PathVariable("publicId") String publicId) {
        Optional<Entry> entry = queueHandlerService.getEntry(publicId);
        if(!entry.isPresent()) {
            return Responses.notFound((String.format("A ticket with public ID %s does not exist", publicId)));
        }

        // More fetchings coming here

        return ResponseEntity.ok(new Resource(ImmutableEntryState.builder().entry(entry.get()).build(), null,  null));
    }
}
