package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/queue")
@CrossOrigin(origins = "${vaco.uiUrl}")
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
    public ResponseEntity<Resource<ImmutableEntry>> createQueueEntry(@Valid @RequestBody ImmutableEntryRequest entryRequest) {
        ImmutableEntry entry = queueHandlerService.processQueueEntry(entryRequest);

        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<ImmutableEntry>>> listEntries(
        JwtAuthenticationToken token,
        @RequestParam String businessId,
        @RequestParam(required = false) boolean full) {
        // TODO: We do not know the exact claim name (or maybe we need to use Graph) at this point, so this is kind of
        //       meh passthrough until we get more details.
        businessId = safeGet(token, vacoProperties.getCompanyNameClaim()).orElse(businessId);
        return ResponseEntity.ok(
            Streams.map(queueHandlerService.getAllQueueEntriesFor(businessId, full), QueueHandlerController::asQueueHandlerResource)
            .toList());
    }

    private Optional<String> safeGet(JwtAuthenticationToken token, String companyNameClaim) {
        if (token != null && token.getTokenAttributes().containsKey(companyNameClaim)) {
            return Optional.of(token.getTokenAttributes().get(companyNameClaim).toString());
        }
        return Optional.empty();
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

        Map<String, Link> links = new HashMap<>();
        links.put("self", linkToGetEntry(entry));

        if (entry.packages() != null) {
            links.putAll(Streams.collect(entry.packages(),
                p -> "package." + p.name(),
                p -> new Link(
                    MvcUriComponentsBuilder
                        .fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), p.name(), null))
                        .toUriString(),
                    RequestMethod.GET)));
        }

        return new Resource<>(entry, links);
    }

    private static Link linkToGetEntry(ImmutableEntry entry) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(QueueHandlerController.class).fetchEntry(entry.publicId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
