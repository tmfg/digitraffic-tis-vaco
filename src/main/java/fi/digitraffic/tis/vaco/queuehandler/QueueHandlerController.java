package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResource;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryView;
import fi.digitraffic.tis.vaco.utils.CustomLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/queue")
public class QueueHandlerController {

    @Autowired
    private QueueHandlerService queueHandlerService;

    @RequestMapping(path = "", method = RequestMethod.POST)
    public EntryResource createQueueEntry(@RequestBody EntryCommand entryCommand) {
        String entryId = queueHandlerService.processQueueEntry(entryCommand);

        EntryResource entryResource = new EntryResource(entryId);
        CustomLink selfLink = new CustomLink(
            linkTo(QueueHandlerController.class).slash(entryId).withSelfRel().getHref(),
            IanaLinkRelations.SELF,
            RequestMethod.GET.name());
        entryResource.add(selfLink);

        return entryResource;
    }

    @RequestMapping(path = "/{publicId}", method = RequestMethod.GET )
    public EntryView getQueueEntryOutcome(@PathVariable("publicId") String publicId) {
        return queueHandlerService.getQueueEntryView(publicId);
    }
}
