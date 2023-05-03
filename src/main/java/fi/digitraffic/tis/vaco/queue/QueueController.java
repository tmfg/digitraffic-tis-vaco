package fi.digitraffic.tis.vaco.queue;

import fi.digitraffic.tis.vaco.queue.entry.QueueEntryCommand;
import fi.digitraffic.tis.vaco.queue.entry.QueueEntryResource;
import fi.digitraffic.tis.vaco.queue.entry.QueueEntryView;
import fi.digitraffic.tis.vaco.utils.CustomLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/queue")
public class QueueController {

    @Autowired
    private QueueService queueService;

    @RequestMapping(path = "", method = RequestMethod.POST)
    public QueueEntryResource createQueueEntry(QueueEntryCommand queueEntryCommand) {
        String entryId = queueService.processQueueEntry(queueEntryCommand);

        QueueEntryResource queueEntryResource = new QueueEntryResource(entryId);
        CustomLink selfLink = new CustomLink(
            linkTo(QueueController.class).slash(entryId).withSelfRel().getHref(),
            IanaLinkRelations.SELF,
            RequestMethod.GET.name());
        queueEntryResource.add(selfLink);

        return queueEntryResource;
    }

    @RequestMapping(path = "/{entryId}", method = RequestMethod.GET )
    public QueueEntryView getQueueEntryOutcome(@PathVariable("entryId") String publicId) {
        return queueService.getQueueEntryView(publicId);
    }
}
