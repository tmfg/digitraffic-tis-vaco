package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
public class QueueHandlerController {

    private final QueueHandlerService queueHandlerService;

    public QueueHandlerController(QueueHandlerService queueHandlerService) {
        this.queueHandlerService = queueHandlerService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableEntry>> createQueueEntry(@Valid @RequestBody ImmutableEntryRequest entryRequest) {
        ImmutableEntry entry = queueHandlerService.processQueueEntry(entryRequest);

        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @RequestMapping(method = RequestMethod.GET, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<ImmutableEntry>>> listEntries(
        @RequestParam String businessId,
        @RequestParam(required = false) boolean full) {
        // TODO: Once we have authentication there needs to be an authentication check that the calling user has access
        //       to the businessId. No authentication yet though, so no such check either.
        return ResponseEntity.ok(queueHandlerService.getAllQueueEntriesFor(businessId, full)
            .stream()
            .map(QueueHandlerController::asQueueHandlerResource)
            .toList());
    }

    @RequestMapping(method = RequestMethod.GET, path = "/{publicId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableEntry>> getQueueEntryOutcome(@PathVariable("publicId") String publicId) {
        Optional<ImmutableEntry> entry = queueHandlerService.getQueueEntryView(publicId);

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
                .fromMethodCall(on(QueueHandlerController.class).getQueueEntryOutcome(entry.publicId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
