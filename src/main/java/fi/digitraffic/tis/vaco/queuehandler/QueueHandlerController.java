package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResult;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResultResource;
import fi.digitraffic.tis.vaco.utils.CustomLink;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/queue")
public class QueueHandlerController {

    private final QueueHandlerService queueHandlerService;

    public QueueHandlerController(QueueHandlerService queueHandlerService) {
        this.queueHandlerService = queueHandlerService;
    }

    @RequestMapping(path = "", method = RequestMethod.POST)
    public EntryResultResource createQueueEntry(@Valid @RequestBody EntryCommand entryCommand) {
        String entryId = queueHandlerService.processQueueEntry(entryCommand);

        EntryResultResource entryResultResource = new EntryResultResource(entryId);
        CustomLink selfLink = new CustomLink(
            linkTo(QueueHandlerController.class).slash(entryId).withSelfRel().getHref(),
            IanaLinkRelations.SELF,
            RequestMethod.GET.name());
        entryResultResource.add(selfLink);

        return entryResultResource;
    }

    @RequestMapping(path = "/{publicId}", method = RequestMethod.GET )
    public EntryResult getQueueEntryOutcome(@PathVariable("publicId") String publicId) {
        return queueHandlerService.getQueueEntryView(publicId);
    }
}
