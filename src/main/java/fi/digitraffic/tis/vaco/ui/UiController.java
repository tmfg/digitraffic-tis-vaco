package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.badges.BadgeController;
import fi.digitraffic.tis.vaco.company.CompanyController;
import fi.digitraffic.tis.vaco.company.dto.PartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerController;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyInfo;
import fi.digitraffic.tis.vaco.ui.model.ImmutableEntryState;
import fi.digitraffic.tis.vaco.ui.model.RuleReport;
import fi.digitraffic.tis.vaco.ui.model.SwapPartnershipRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private final CompanyHierarchyService companyHierarchyService;

    private final PackagesService packagesService;

    private final AdminToolsService adminToolsService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public UiController(VacoProperties vacoProperties,
                        EntryStateService entryStateService,
                        QueueHandlerService queueHandlerService,
                        RulesetService rulesetService,
                        MeService meService,
                        CompanyHierarchyService companyHierarchyService,
                        PackagesService packagesService,
                        AdminToolsService adminToolsService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.entryStateService = Objects.requireNonNull(entryStateService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.meService = Objects.requireNonNull(meService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.adminToolsService = Objects.requireNonNull(adminToolsService);
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

    @PostMapping(path = "/queue")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('vaco.user')")
    public ResponseEntity<Resource<Entry>> createQueueEntry(@Valid @RequestBody EntryRequest entryRequest) {
        Entry entry = queueHandlerService.processQueueEntry(entryRequest);
        return ResponseEntity.ok(asQueueHandlerResource(entry));
    }

    @GetMapping(path = "/rules")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('vaco.user')")
    public ResponseEntity<Set<Resource<Ruleset>>> listRulesets(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(
                Streams.collect(rulesetService.selectRulesets(businessId), Resource::resource));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
                    if (rulesets.get(task.name()) != null) {
                        report = entryStateService.getRuleReport(task, entry, rulesets);
                    }
                    if(report != null) {
                        reports.add(report);
                    }
                });
                List<Summary> summaries = entryStateService.getTaskSummaries(entry);
                Optional<Company> company = companyHierarchyService.findByBusinessId(entry.businessId());
                return ResponseEntity.ok(Resource.resource(
                    ImmutableEntryState.builder()
                        .entry(asEntryStateResource(entry))
                        .reports(reports)
                        .summaries(summaries)
                        .company(company.map(c -> c.name() + " (" +c.businessId() + ")").orElse(entry.businessId()))
                        .build()));
            }).orElseGet(() -> Responses.notFound((String.format("Entry with public id %s does not exist", publicId))));
    }

    @GetMapping(path = "/entries")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('vaco.user')")
    public ResponseEntity<List<Resource<Entry>>> listEntries(@RequestParam(name = "full") boolean full) {
        List<Entry> entries = queueHandlerService.getAllEntriesVisibleForCurrentUser(full);
        return ResponseEntity.ok(Streams.collect(entries, this::asEntryStateResource));
    }

    @GetMapping(path = "/packages/{entryPublicId}/{taskName}/{packageName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('vaco.user')")
    public FileSystemResource fetchPackage(
        @PathVariable("entryPublicId") String entryPublicId,
        @PathVariable("taskName") String taskName,
        @PathVariable("packageName") String packageName,
        HttpServletResponse response) {
        // TODO: Add MeService check here !
        return queueHandlerService.findEntry(entryPublicId)
            .flatMap(e -> Streams.filter(e.tasks(), t -> t.name().equals(taskName))
                .findFirst()
                .flatMap(t -> packagesService.downloadPackage(e, t, packageName)))
            .map(filePath -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(packageName + ".zip")
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

                return new FileSystemResource(filePath);
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("Unknown package '%s' for entry '%s'", packageName, entryPublicId)));
    }

    @GetMapping(path = "/admin/data-delivery")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<List<Resource<CompanyLatestEntry>>> fetchLatestEntriesPerCompany() {
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService
            .listLatestEntriesPerCompany(
                meService.isAdmin()
                    ? null
                    : meService.findCompanies());
        return ResponseEntity.ok(Streams.collect(companyLatestEntries, this::asCompanyLatestEntryResource));
    }

    @GetMapping(path = "/admin/entries")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<List<Resource<Entry>>> listCompanyEntries(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            List<Entry> entries = queueHandlerService.getAllQueueEntriesFor(businessId, false);
            return ResponseEntity.ok(Streams.collect(entries, this::asEntryStateResource));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping(path = "/admin/companies/{businessId}/info")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<ImmutableCompanyInfo>> getCompanyInfo(@PathVariable("businessId") String businessId) {
        return companyHierarchyService.findByBusinessId(businessId)
            .filter(meService::isAllowedToAccess)
            .map(company -> ResponseEntity.ok(new Resource<>(ImmutableCompanyInfo.builder()
                .company(company)
                .hierarchies(companyHierarchyService.getHierarchiesContainingCompany(businessId))
                .rulesets(rulesetService.selectRulesets(businessId))
                .build(), null, Map.of()))
            ).orElseGet(() ->
                Responses.notFound((String.format("Company with business id %s either does not exist or not authorized to be accessed", businessId))));
    }

    @GetMapping(path = "/admin/companies/hierarchy")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Hierarchy>>> getHierarchies(@RequestParam("businessId") String businessId) {
        return ResponseEntity.ok(new Resource<>(companyHierarchyService.getHierarchies(businessId), null, null));
    }

    @PutMapping(path = "/admin/companies/{businessId}")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<ImmutableCompanyInfo>> editCompany(
        @PathVariable("businessId") String businessId,
        @Valid @RequestBody Company company) {
        return companyHierarchyService.findByBusinessId(businessId)
            .filter(meService::isAllowedToAccess)
            .map(c -> {
                Company updatedCompany = companyHierarchyService.editCompany(businessId, company);
                return ResponseEntity.ok(
                    new Resource<>(
                        ImmutableCompanyInfo.builder()
                            .company(updatedCompany)
                            .hierarchies(companyHierarchyService.getHierarchiesContainingCompany(updatedCompany.businessId()))
                            .rulesets(rulesetService.selectRulesets(updatedCompany.businessId()))
                            .build(),
                        null, Map.of())); })
            .orElseGet(() ->
                Responses.notFound((String.format("Company with business id %s either does not exist or not authorized to be accessed", businessId))));
    }

    @PostMapping(path = "/admin/partnership")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Hierarchy>>> createPartnership(
        @RequestBody @Valid PartnershipRequest partnershipRequest) {
        String partnerABusinessId = partnershipRequest.partnerABusinessId();
        String partnerBBusinessId = partnershipRequest.partnerBBusinessId();

        Optional<Company> partnerA = companyHierarchyService.findByBusinessId(partnerABusinessId);
        Optional<Company> partnerB = companyHierarchyService.findByBusinessId(partnerBBusinessId);

        if (partnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Either of the provided company' business ID (%s, %s) does not exist",
                    partnerABusinessId, partnerBBusinessId));
        }

        if (!(meService.isAdmin() || meService.findCompanies().contains(partnerB.get()))) {
            // Let's check that company admin has right to edit anything for partnerB
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("User does not have rights to create partnership for company %s (%s)",
                    partnerB.get().name(), partnerB.get().businessId()));
        }

        if (companyHierarchyService.findPartnership(PartnershipType.AUTHORITY_PROVIDER, partnerA.get(), partnerB.get()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Cannot create partnership between companies %s and %s because it already exists",
                    partnerA.get().businessId(), partnerB.get().businessId()));
        }

        if (companyHierarchyService.findPartnership(PartnershipType.AUTHORITY_PROVIDER, partnerB.get(), partnerA.get()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Cannot create partnership between companies %s and %s because that would result in a circular relation",
                    partnerA.get().businessId(), partnerB.get().businessId()));
        }

        return ResponseEntity.ok(new Resource<>(companyHierarchyService.createPartnershipAndReturnUpdatedHierarchy(
            PartnershipType.AUTHORITY_PROVIDER, partnerA.get(), partnerB.get()), null, null));
    }

    @DeleteMapping(path = "/admin/partnership")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Hierarchy>>> deletePartnership(
        @RequestParam("partnerABusinessId") String partnerABusinessId,
        @RequestParam("partnerBBusinessId") String partnerBBusinessId) {
        Optional<Company> partnerA = companyHierarchyService.findByBusinessId(partnerABusinessId);
        Optional<Company> partnerB = companyHierarchyService.findByBusinessId(partnerBBusinessId);

        if (partnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Either of the provided company' business ID (%s, %s) does not exist",
                    partnerABusinessId, partnerBBusinessId));
        }

        if (!meService.isAdmin() && !meService.findCompanies().contains(partnerB.get())) {
            // Let's check that company admin has right to edit anything for partnerB
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("User does not have rights to remove partnership for company %s (%s)",
                    partnerB.get().name(), partnerB.get().businessId()));
        }

        return companyHierarchyService.findPartnership(PartnershipType.AUTHORITY_PROVIDER, partnerA.get(), partnerB.get())
            .map(partnership ->
                ResponseEntity.ok(new Resource<>(companyHierarchyService.deletePartnership(partnership), null, null))
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Partnership between (%s, %s) cannot be deleted because it does not exist",
                    partnerABusinessId, partnerBBusinessId)));
    }

    @PostMapping(path = "/admin/partnership/swap")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Hierarchy>>> swapPartnership(
        @Valid @RequestBody SwapPartnershipRequest swapPartnershipRequest) {
        String oldPartnerABusinessId = swapPartnershipRequest.oldPartnerABusinessId();
        String newPartnerABusinessId = swapPartnershipRequest.newPartnerABusinessId();
        String partnerBBusinessId = swapPartnershipRequest.partnerBBusinessId();

        Optional<Company> oldPartnerA = companyHierarchyService.findByBusinessId(oldPartnerABusinessId);
        Optional<Company> newPartnerA = companyHierarchyService.findByBusinessId(newPartnerABusinessId);
        Optional<Company> partnerB = companyHierarchyService.findByBusinessId(partnerBBusinessId);

        if (oldPartnerA.isEmpty() || newPartnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Either of the provided company' business ID (%s, %s, %s) does not exist",
                    oldPartnerABusinessId, newPartnerABusinessId, partnerBBusinessId));
        }

        if (!(meService.isAdmin() || meService.findCompanies().contains(partnerB.get()))) {
            // Let's check that company admin has right to edit anything for partnerB
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("User does not have rights to remove partnership for company %s (%s)",
                    partnerB.get().name(), partnerB.get().businessId()));
        }

        Optional<Partnership> existingPartnership = companyHierarchyService
            .findPartnership(PartnershipType.AUTHORITY_PROVIDER, oldPartnerA.get(), partnerB.get());
        if (existingPartnership.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Partnership between %s and %s does not exist, hence swapping is not possible",
                    oldPartnerA.get().name(), partnerB.get().businessId()));
        }

        Optional<Partnership> potentialDuplicatePartnership = companyHierarchyService
            .findPartnership(PartnershipType.AUTHORITY_PROVIDER, newPartnerA.get(), partnerB.get());
        if (potentialDuplicatePartnership.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Partnership between %s and %s already exists, hence swapping is not possible",
                    newPartnerA.get().name(), partnerB.get().businessId()));
        }

        return ResponseEntity.ok(new Resource<>(companyHierarchyService.swapPartnership(
            newPartnerA.get(), partnerB.get(), existingPartnership.get()), null, null));
    }

    @GetMapping(path = "/admin/companies")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<CompanyWithFormatSummary>>> listCompanies() {
        return ResponseEntity.ok(new Resource<>(adminToolsService.getRelevantCompanies(), null, null));
    }

    private Resource<CompanyLatestEntry> asCompanyLatestEntryResource(CompanyLatestEntry companyLatestEntry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        Map<String, Link> refs = new HashMap<>();
        if (companyLatestEntry.publicId() != null) {
            refs.put("badge", Link.to(vacoProperties.baseUrl(),
                RequestMethod.GET,
                fromMethodCall(on(BadgeController.class).entryBadge(companyLatestEntry.publicId(), null))));
        }
        links.put("refs", refs);
        return new Resource<>(companyLatestEntry, null, links);
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

    private Resource<Entry> asQueueHandlerResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", Link.to(vacoProperties.baseUrl(), RequestMethod.GET, fromMethodCall(on(QueueHandlerController.class).fetchEntry(entry.publicId())))));

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

    private Resource<Company> asCompanyResource(Company company) {
        return new Resource<>(company, null, Map.of("refs", Map.of("self", Link.to(vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(CompanyController.class).getCompanyByBusinessId(company.businessId()))))));
    }
}
