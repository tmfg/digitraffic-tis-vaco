package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/queue")
@CrossOrigin(origins = "${vaco.uiUrl}")
public class QueueHandlerController {

    private final QueueHandlerService queueHandlerService;

    public QueueHandlerController(QueueHandlerService queueHandlerService) {
        this.queueHandlerService = queueHandlerService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableEntry>> createQueueEntry(@Valid @RequestBody ImmutableEntryRequest entryRequest) {
        ImmutableEntry entry = queueHandlerService.processQueueEntry(entryRequest);

        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<ImmutableEntry>>> listEntries(
        @RequestParam String businessId,
        @RequestParam(required = false) boolean full) {
        // TODO: Once we have authentication there needs to be an authentication check that the calling user has access
        //       to the businessId. No authentication yet though, so no such check either.
        return ResponseEntity.ok(
            Streams.map(queueHandlerService.getAllQueueEntriesFor(businessId, full), QueueHandlerController::asQueueHandlerResource)
            .toList());
    }

    @GetMapping(path = "/{publicId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableEntry>> fetchEntry(@PathVariable("publicId") String publicId) {
        Optional<ImmutableEntry> entry = queueHandlerService.getEntry(publicId);

        return entry
            .map(e -> ResponseEntity.ok(asQueueHandlerResource(e)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("A ticket with public ID %s does not exist", publicId)));
    }

    private static Resource<ImmutableEntry> asQueueHandlerResource(ImmutableEntry entry) {
        return new Resource<>(entry, Map.of("self", linkToGetEntry(entry)));
    }

    private static Link linkToGetEntry(ImmutableEntry entry) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(QueueHandlerController.class).fetchEntry(entry.publicId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
