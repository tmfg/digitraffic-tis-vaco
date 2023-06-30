package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.Link;
import fi.digitraffic.tis.vaco.queuehandler.dto.QueueHandlerResource;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
    public ResponseEntity<QueueHandlerResource<ImmutableQueueEntry>> createQueueEntry(@Valid @RequestBody ImmutableEntryCommand entryCommand) {
        ImmutableQueueEntry entry = queueHandlerService.processQueueEntry(entryCommand);

        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @RequestMapping(method = RequestMethod.GET, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<QueueHandlerResource<ImmutableQueueEntry>>> listEntries(
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
    public ResponseEntity<QueueHandlerResource<ImmutableQueueEntry>> getQueueEntryOutcome(@PathVariable("publicId") String publicId) {
        Optional<ImmutableQueueEntry> entry = queueHandlerService.getQueueEntryView(publicId);

        return ResponseEntity.ok(new QueueHandlerResource<>(entry.orElse(null), Map.of()));
    }

    private static QueueHandlerResource<ImmutableQueueEntry> asQueueHandlerResource(ImmutableQueueEntry entry) {
        return new QueueHandlerResource<>(entry, Map.of("self", linkToGetEntry(entry)));
    }

    private static Link linkToGetEntry(ImmutableQueueEntry entryId) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(QueueHandlerController.class).getQueueEntryOutcome(entryId.publicId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
