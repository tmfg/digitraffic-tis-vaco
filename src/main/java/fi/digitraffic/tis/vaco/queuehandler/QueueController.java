package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping({"/v1/queue", "/queue"})
@PreAuthorize("hasAuthority('vaco.apiuser')")
public class QueueController {

    private final EncryptionService encryptionService;
    private final MeService meService;
    private final QueueHandlerService queueHandlerService;
    private final EntryService entryService;
    private final VacoProperties vacoProperties;
    private final EntryRequestMapper entryRequestMapper;

    public QueueController(MeService meService,
                           QueueHandlerService queueHandlerService,
                           EntryService entryService,
                           VacoProperties vacoProperties,
                           EntryRequestMapper entryRequestMapper, EncryptionService encryptionService) {
        this.meService = Objects.requireNonNull(meService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.entryService = Objects.requireNonNull(entryService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.entryRequestMapper = Objects.requireNonNull(entryRequestMapper);
        this.encryptionService = encryptionService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Entry>> createQueueEntry(@Valid @RequestBody CreateEntryRequest createEntryRequest) {
        Entry converted = entryRequestMapper.toEntry(createEntryRequest);

        return queueHandlerService.processQueueEntry(converted)
            .map(e -> ResponseEntity.ok(asQueueHandlerResource(e)))
            .orElse(Responses.badRequest("Failed to create entry from request"));
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<List<Resource<Entry>>> listEntries(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(
                Streams.collect(queueHandlerService.getAllQueueEntriesFor(businessId), this::asQueueHandlerResource));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping(path = "/{publicId}")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Entry>> fetchEntry(@PathVariable("publicId") String publicId) {
        return entryService.findEntry(publicId)
            .filter(meService::isAllowedToAccess)
            .map(e -> ResponseEntity.ok(asQueueHandlerResource(e)))
            .orElse(Responses.notFound(String.format("Entry with public id %s does not exist", publicId)));
    }

    private Resource<Entry> asQueueHandlerResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();

        String magicToken = encryptionService.encrypt(ImmutableMagicToken.of(entry.publicId()));

        Link magicLink = new Link(vacoProperties.baseUrl() + "/ui/data/" + entry.publicId() + "?magic=" + magicToken, RequestMethod.GET);
        Link selfLink = Link.to(vacoProperties.baseUrl(), RequestMethod.GET, fromMethodCall(on(QueueController.class).fetchEntry(entry.publicId())));
        links.put( "refs", Map.of("self", selfLink, "magic", magicLink));

        Map<Long, Task> tasks = Streams.collect(entry.tasks(), Task::id, Function.identity());

        if (entry.packages() != null) {
            ConcurrentMap<String, Map<String, Link>> packageLinks = new ConcurrentHashMap<>();
            entry.packages().forEach(p -> {
                String taskName = tasks.get(p.taskId()).name();
                packageLinks.computeIfAbsent(taskName, t -> new HashMap<>()).put(p.name(), Link.to(vacoProperties.baseUrl(), RequestMethod.GET, fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), taskName, p.name(), null))));
            });

            links.putAll(packageLinks);
        }

        return new Resource<>(entry, null, links);
    }

}
