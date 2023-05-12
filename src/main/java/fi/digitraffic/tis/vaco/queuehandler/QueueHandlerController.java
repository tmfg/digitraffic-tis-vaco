package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResult;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResultResource;
import fi.digitraffic.tis.vaco.utils.Link;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.util.Map;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/queue")
public class QueueHandlerController {

    private final QueueHandlerService queueHandlerService;

    public QueueHandlerController(QueueHandlerService queueHandlerService) {
        this.queueHandlerService = queueHandlerService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    public ResponseEntity<EntryResultResource> createQueueEntry(@Valid @RequestBody EntryCommand entryCommand) {
        String entryId = queueHandlerService.processQueueEntry(entryCommand);
        String selfLink = MvcUriComponentsBuilder.fromMethodCall(on(QueueHandlerController.class).getQueueEntryOutcome(entryId)).toUriString();

        var links = Map.of("self", new Link(selfLink, RequestMethod.GET));

        EntryResultResource entryResultResource = new EntryResultResource(entryId, links);

        return ResponseEntity.ok(entryResultResource);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/{publicId}")
    public ResponseEntity<EntryResult> getQueueEntryOutcome(@PathVariable("publicId") String publicId) {
        return ResponseEntity.ok(queueHandlerService.getQueueEntryView(publicId));
    }
}
