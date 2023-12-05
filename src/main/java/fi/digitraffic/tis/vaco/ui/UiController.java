package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import fi.digitraffic.tis.vaco.ui.model.ImmutableEntryState;
import fi.digitraffic.tis.vaco.ui.model.ValidationReport;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static fi.digitraffic.tis.utilities.JwtHelpers.safeGet;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/ui")
public class UiController {

    private final VacoProperties vacoProperties;

    private final EntryStateService entryStateService;

    private final QueueHandlerService queueHandlerService;

    private final RulesetRepository rulesetRepository;

    public UiController(VacoProperties vacoProperties, EntryStateService entryStateService, QueueHandlerService queueHandlerService, RulesetRepository rulesetRepository) {
        this.vacoProperties = vacoProperties;
        this.entryStateService = entryStateService;
        this.queueHandlerService = queueHandlerService;
        this.rulesetRepository = rulesetRepository;
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
    public ResponseEntity<Resource<ImmutableEntryState>> fetchEntryState(@PathVariable("publicId") String publicId) {
        Optional<Entry> entryOpt = queueHandlerService.findEntry(publicId, true);
        if(entryOpt.isEmpty()) {
            return Responses.notFound((String.format("A ticket with public ID %s does not exist", publicId)));
        }

        Entry entry = entryOpt.get();
        Map<String, Task> tasksByName = Streams.collect(entry.tasks(), Task::name, Function.identity());
        Map<String, Ruleset> rulesets = rulesetRepository.findRulesetsAsMap("2942108-7");
        List<ValidationReport> validationReports =  new ArrayList<>();
        entry.validations().forEach(validationInput -> {
            Task ruleTask = tasksByName.get(validationInput.name());
            if (ruleTask != null) {
                ValidationReport report = entryStateService.getValidationReport(ruleTask, rulesets.get(validationInput.name()));
                if(report != null) {
                    validationReports.add(report);
                }
            }
        });

        List<Resource<Package>> validationPackages = new ArrayList<>();
        Map<Long, Task> tasksById = Streams.collect(entry.tasks(), Task::id, Function.identity());
        entry.packages().forEach(p -> {
            Task task = tasksById.get(p.taskId());
            validationPackages.add(asPackageResource(p, task, entry));
        });

        return ResponseEntity.ok(new Resource(ImmutableEntryState.builder()
            .entry(entry)
            .validationReports(validationReports)
            .validationPackages(validationPackages)
            .build(), null,  null));
    }

    @GetMapping(path = "/entries")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<Entry>>> listEntries(
        JwtAuthenticationToken token,
        @RequestParam("businessId") String businessId,
        @RequestParam("full") boolean full) {
        businessId = safeGet(token, vacoProperties.companyNameClaim()).orElse(businessId);
        List<Entry> entries = queueHandlerService.getAllQueueEntriesFor(businessId, full);
        return ResponseEntity.ok(
            Streams.map(entries, UiController::asEntryStateResource)
                .toList());
    }

    private static Resource<Entry> asEntryStateResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", linkToGetEntryState(entry)));
        return new Resource<>(entry, null, links);
    }

    private static Link linkToGetEntryState(Entry entry) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(UiController.class).fetchEntryState(entry.publicId()))
                .toUriString(),
            RequestMethod.GET);
    }

    private static Resource<Package> asPackageResource(Package taskPackage, Task task, Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", linkToGetPackage(taskPackage, task, entry)));
        return new Resource<>(taskPackage, null, links);
    }

    private static Link linkToGetPackage(Package taskPackage, Task task, Entry entry) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), task.name(), taskPackage.name(), null))
                .toUriString(),
            RequestMethod.GET);
    }
}
