package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static fi.digitraffic.tis.utilities.JwtHelpers.safeGet;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/queue")
public class QueueHandlerController {

    private final QueueHandlerService queueHandlerService;
    private final VacoProperties vacoProperties;

    public QueueHandlerController(QueueHandlerService queueHandlerService,
                                  VacoProperties vacoProperties) {
        this.queueHandlerService = queueHandlerService;
        this.vacoProperties = vacoProperties;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<Entry>> createQueueEntry(@Valid @RequestBody EntryRequest entryRequest) {
        Entry entry = queueHandlerService.processQueueEntry(entryRequest);

        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<Entry>>> listEntries(
        JwtAuthenticationToken token,
        @RequestParam(name = "businessId") String businessId,
        @RequestParam(name = "full", required = false) boolean full) {
        // TODO: We do not know the exact claim name (or maybe we need to use Graph) at this point, so this is kind of
        //       meh passthrough until we get more details.
        businessId = safeGet(token, vacoProperties.companyNameClaim()).orElse(businessId);
        return ResponseEntity.ok(
            Streams.map(queueHandlerService.getAllQueueEntriesFor(businessId, full), this::asQueueHandlerResource)
            .toList());
    }

    @GetMapping(path = "/{publicId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<Entry>> fetchEntry(@PathVariable("publicId") String publicId) {
        Optional<Entry> entry = queueHandlerService.findEntry(publicId);

        return entry
            .map(e -> ResponseEntity.ok(asQueueHandlerResource(e)))
            .orElse(Responses.notFound(String.format("A ticket with public ID %s does not exist", publicId)));
    }

    private Resource<Entry> asQueueHandlerResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", linkToGetEntry(entry)));

        Map<Long, Task> tasks = Streams.collect(entry.tasks(), Task::id, Function.identity());

        if (entry.packages() != null) {
            ConcurrentMap<String, Map<String, Link>> packageLinks = new ConcurrentHashMap<>();
            entry.packages().forEach(p -> {
                String taskName = tasks.get(p.taskId()).name();
                packageLinks.computeIfAbsent(taskName, t -> new HashMap<>())
                    .put(p.name(),
                        constructLink(
                            vacoProperties,
                            MvcUriComponentsBuilder.fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), taskName, p.name(), null)),
                            RequestMethod.GET));
            });

            links.putAll(packageLinks);
        }

        return new Resource<>(entry, null, links);
    }

    private Link linkToGetEntry(Entry entry) {
        return constructLink(
            vacoProperties,
            MvcUriComponentsBuilder.fromMethodCall(on(QueueHandlerController.class).fetchEntry(entry.publicId())),
            RequestMethod.GET);
    }

    private Link constructLink(VacoProperties vacoProperties,
                               UriComponentsBuilder uriComponentsBuilder,
                               RequestMethod method) {
        URI baseUri = URI.create(vacoProperties.baseUrl());
        return new Link(uriComponentsBuilder
            .scheme(baseUri.getScheme())
            .host(baseUri.getHost())
            .port(baseUri.getPort())
            .toUriString(),
            method);
    }
}
