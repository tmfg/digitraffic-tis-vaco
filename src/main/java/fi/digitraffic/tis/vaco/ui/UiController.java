package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.badges.BadgeController;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.model.EntryState;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import fi.digitraffic.tis.vaco.ui.model.ImmutableEntryState;
import fi.digitraffic.tis.vaco.ui.model.RuleReport;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/ui")
public class UiController {

    private final VacoProperties vacoProperties;

    private final EntryStateService entryStateService;

    private final QueueHandlerService queueHandlerService;

    private final RulesetService rulesetService;

    private final MeService meService;

    public UiController(VacoProperties vacoProperties,
                        EntryStateService entryStateService,
                        QueueHandlerService queueHandlerService,
                        RulesetService rulesetService,
                        MeService meService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.entryStateService = Objects.requireNonNull(entryStateService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.meService = Objects.requireNonNull(meService);
    }

    @GetMapping(path = "/bootstrap")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableBootstrap> bootstrap() {
        return ResponseEntity.ok()
            .body(ImmutableBootstrap.of(
                vacoProperties.environment(),
                vacoProperties.baseUrl(),
                vacoProperties.azureAd().tenantId(),
                vacoProperties.azureAd().clientId()));
    }

    @GetMapping(path = "/entries/{publicId}/state")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('vaco.user')")
    public ResponseEntity<Resource<ImmutableEntryState>> fetchEntryState(@PathVariable("publicId") String publicId) {
        return queueHandlerService.findEntry(publicId, true)
            .filter(meService::isAllowedToAccess)
            .map(entry -> {
                Map<String, Ruleset> rulesets = Streams.collect(rulesetService.selectRulesets(entry.businessId()), Ruleset::identifyingName, Function.identity());
                List<RuleReport> reports =  new ArrayList<>();
                entry.tasks().forEach(task -> {
                    RuleReport report = null;
                    if (RuleName.VALIDATION_RULES.contains(task.name()) || RuleName.CONVERSION_RULES.contains(task.name())) {
                        report = entryStateService.getRuleReport(task, entry, rulesets);
                    }
                    if(report != null) {
                        reports.add(report);
                    }
                });
                List<Summary> summaries = entryStateService.getTaskSummaries(entry);
                return ResponseEntity.ok(Resource.resource(
                    ImmutableEntryState.builder()
                        .entry(asEntryStateResource(entry))
                        .reports(reports)
                        .build()));
            }).orElseGet(() -> Responses.notFound((String.format("Entry with public id %s does not exist", publicId))));
    }

    @GetMapping(path = "/entries")
    @JsonView(DataVisibility.External.class)
    //@PreAuthorize("hasAuthority('vaco.user')")
    public ResponseEntity<List<Resource<Entry>>> listEntries(@RequestParam(name = "full") boolean full) {
        List<Entry> entries = queueHandlerService.getAllEntriesVisibleForCurrentUser(full);
        return ResponseEntity.ok(Streams.collect(entries, this::asEntryStateResource));
    }

    private Resource<Entry> asEntryStateResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        Map<String, Link> linkInstances = new HashMap<>();
        linkInstances.put("self", Link.to(vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(UiController.class).fetchEntryState(entry.publicId()))));
        linkInstances.put("badge", Link.to(vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(BadgeController.class).entryBadge(entry.publicId(), null))));

        links.put("refs", linkInstances);
        return new Resource<>(entry, null, links);
    }
}
