package fi.digitraffic.tis.vaco.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/queue")
public class QueueController {

    @Autowired
    private QueueService queueService;

    @RequestMapping(path = "", method = RequestMethod.POST)
    public QueueEntryResource createQueueEntry(QueueEntryCommand queueEntryCommand) {
        String ticketId = queueService.processQueueEntry(queueEntryCommand);

        QueueEntryResource queueEntryResource = new QueueEntryResource(ticketId);
        Link selfLink = linkTo(QueueController.class).slash(ticketId).withSelfRel();
        queueEntryResource.add(selfLink);

        return queueEntryResource;
    }

    @RequestMapping(path = "/{ticketId}", method = RequestMethod.GET )
    public QueueEntryView getQueueEntryOutcome(@PathVariable("ticketId") String ticketId) {
        return queueService.getQueueEntryOutcome(ticketId);
    }
}
