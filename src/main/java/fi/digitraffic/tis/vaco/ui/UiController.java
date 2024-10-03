package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.badges.BadgeController;
import fi.digitraffic.tis.vaco.company.dto.PartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.queuehandler.QueueController;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.mapper.UiModelMapper;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import fi.digitraffic.tis.vaco.ui.model.Context;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyInfo;
import fi.digitraffic.tis.vaco.ui.model.ImmutableEntryState;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicTokenResponse;
import fi.digitraffic.tis.vaco.ui.model.ImmutableProcessingResultsPage;
import fi.digitraffic.tis.vaco.ui.model.MagicToken;
import fi.digitraffic.tis.vaco.ui.model.MagicTokenResponse;
import fi.digitraffic.tis.vaco.ui.model.MyDataEntrySummary;
import fi.digitraffic.tis.vaco.ui.model.SwapPartnershipRequest;
import fi.digitraffic.tis.vaco.ui.model.TaskReport;
import fi.digitraffic.tis.vaco.ui.model.pages.CompanyEntriesPage;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
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
@Hidden
public class UiController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final UiService uiService;

    private final VacoProperties vacoProperties;
    private final EntryService entryService;
    private final TaskService taskService;
    private final EntryStateService entryStateService;
    private final QueueHandlerService queueHandlerService;
    private final RulesetService rulesetService;
    private final MeService meService;
    private final CompanyHierarchyService companyHierarchyService;
    private final PackagesService packagesService;
    private final AdminToolsService adminToolsService;
    private final EntryRequestMapper entryRequestMapper;
    private final EncryptionService encryptionService;
    private final ContextService contextService;
    private final UiModelMapper uiModelMapper;

    public UiController(VacoProperties vacoProperties,
                        EntryService entryService,
                        TaskService taskService,
                        EntryStateService entryStateService,
                        QueueHandlerService queueHandlerService,
                        RulesetService rulesetService,
                        MeService meService,
                        CompanyHierarchyService companyHierarchyService,
                        PackagesService packagesService,
                        AdminToolsService adminToolsService,
                        EntryRequestMapper entryRequestMapper,
                        EncryptionService encryptionService,
                        UiService uiService,
                        ContextService contextService,
                        UiModelMapper uiModelMapper) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.entryService = Objects.requireNonNull(entryService);
        this.taskService = Objects.requireNonNull(taskService);
        this.entryStateService = Objects.requireNonNull(entryStateService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.meService = Objects.requireNonNull(meService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.adminToolsService = Objects.requireNonNull(adminToolsService);
        this.entryRequestMapper = Objects.requireNonNull(entryRequestMapper);
        this.encryptionService = Objects.requireNonNull(encryptionService);
        this.uiService = Objects.requireNonNull(uiService);
        this.contextService = Objects.requireNonNull(contextService);
        this.uiModelMapper = Objects.requireNonNull(uiModelMapper);
    }

    @GetMapping(path = "/bootstrap")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<ImmutableBootstrap> bootstrap() {
        return ResponseEntity.ok()
            .body(ImmutableBootstrap.of(
                vacoProperties.environment(),
                vacoProperties.baseUrl(),
                vacoProperties.azureAd().tenantId(),
                vacoProperties.azureAd().clientId(),
                bootstrapBuildInfo()));
    }

    private @Value("${git.build.version:#{'latest'}}") String buildVersion;
    private @Value("${git.commit.id.full:#{'HEAD'}}") String commitId;

    private String bootstrapBuildInfo() {
        return buildVersion + " (" + commitId + ")";
    }


    @PostMapping(path = "/queue")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Entry>> createQueueEntry(@Valid @RequestBody CreateEntryRequest createEntryRequest) {
        Entry converted = entryRequestMapper.toEntry(createEntryRequest);
        return queueHandlerService.processQueueEntry(converted)
            .map(e -> ResponseEntity.ok(asQueueHandlerResource(e)))
            .orElse(Responses.badRequest("Failed to create entry from request"));
    }

    @GetMapping(path = "/rules")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Set<Resource<Ruleset>>> listRulesets(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(
                Streams.collect(rulesetService.selectRulesets(businessId), Resource::resource));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping(path = "/contexts")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<List<Resource<Context>>> listContexts(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(Streams.map(contextService.findByBusinessId(businessId), Resource::resource).toList());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private void validateContext(Context context) {
        Optional<Company> companyRecord = companyHierarchyService.findByBusinessId(context.businessId());
        if (companyRecord.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("Provided company' business ID (%s) does not exist", context.businessId()));
        }
        Optional<Context> contextRecord = contextService.find(context.context().trim().toLowerCase(), context.businessId());
        if (contextRecord.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                String.format("Context (%s) with company' business ID (%s) already exists", context.context(), context.businessId()));
        }
    }

    @PostMapping(path = "/admin/contexts")
    @JsonView(DataVisibility.Public.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Context>>> createContext(@RequestBody @Valid Context context) {
        if (meService.isAdmin() || meService.isAllowedToAccess(context.businessId())) {
            validateContext(context);
            return ResponseEntity.ok(new Resource<>(contextService.create(context), null, null));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping(path = "/admin/contexts/{contextIdentifier}")
    @JsonView(DataVisibility.Public.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Context>>> editContext(@PathVariable("contextIdentifier") String contextIdentifier,
                                                               @RequestBody @Valid Context context) {
        if (meService.isAdmin() || meService.isAllowedToAccess(context.businessId())) {
            validateContext(context);
            return ResponseEntity.ok(new Resource<>(contextService.update(contextIdentifier, context), null, null));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Handler for returning the initial state for Processing Results Page.
     *
     * @param publicId Mandatory public id of the page
     * @return {@link fi.digitraffic.tis.vaco.ui.model.ProcessingResultsPage}
     */
    /*
     * TODO: This same response should also include the entry itself, but as that is now quite a hodge-podge of nested
     *       things instead of well-defined type structure, this won't be used for that at this time.
     */
    @GetMapping(path = "/processing-results/{publicId}")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<ImmutableProcessingResultsPage>> processingResultsPage(@PathVariable("publicId") String publicId) {
        return entryService.findEntry(publicId)
            .map(entry -> ResponseEntity.ok(
                Resource.resource(
                    ImmutableProcessingResultsPage.of(
                        encryptionService.encrypt(ImmutableMagicToken.of(publicId))
                    )
                )
            ))
            .orElseGet(() -> Responses.unauthorized(String.format("Not allowed to access %s", publicId)));
    }

    @GetMapping(path = "/entries/{publicId}/state")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<ImmutableEntryState>> fetchEntryState(@PathVariable("publicId") String publicId,
                                                                         @RequestParam(value = "magic", required = false) String magicToken) {
        return entryService.findEntry(publicId)
            .filter(e -> magicTokenMatches(magicToken, publicId) || meService.isAllowedToAccess(e))
            .map(entry -> {
                Map<String, Ruleset> rulesets = Streams.collect(rulesetService.selectRulesets(entry.businessId()), Ruleset::identifyingName, Function.identity());
                List<TaskReport> reports =  new ArrayList<>();
                entry.tasks().forEach(task -> {
                    TaskReport report = null;
                    report = entryStateService.getTaskReport(task, entry, rulesets);
                    if(report != null) {
                        reports.add(report);
                    }
                });
                List<Summary> summaries = entryStateService.getTaskSummaries(entry);
                Optional<Company> company = companyHierarchyService.findByBusinessId(entry.businessId());
                return ResponseEntity.ok(Resource.resource(
                    ImmutableEntryState.builder()
                        .entry(asEntryStateResource(entry, entry.publicId()))
                        .reports(reports)
                        .addAllSummaries(summaries)
                        .company(company.map(c -> c.name() + " (" +c.businessId() + ")").orElse(entry.businessId()))
                        .build()));
            }).orElseGet(() -> Responses.notFound((String.format("Entry with public id %s does not exist", publicId))));
    }

    private boolean magicTokenMatches(String magicToken, String publicId) {
        boolean success = false;
        if (magicToken != null) {
            MagicToken decrypted = encryptionService.decrypt(magicToken, MagicToken.class);
            success = decrypted.token().equals(publicId);
        }
        logger.info("{} access to {} with magic link {}", meService.alertText(), publicId, success ? "allowed" : "denied");
        return success;
    }

    @GetMapping(path = "/entries")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<List<Resource<MyDataEntrySummary>>> listEntries(@RequestParam(name = "full") boolean full) {
        List<MyDataEntrySummary> entries = uiService.getAllEntriesVisibleForCurrentUser();
        return ResponseEntity.ok(Streams.collect(entries, e -> asEntryStateResource(e, e.publicId())));
    }

    @GetMapping(path = "/packages/{entryPublicId}/{taskName}/{packageName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public FileSystemResource fetchPackage(
        @PathVariable("entryPublicId") String entryPublicId,
        @PathVariable("taskName") String taskName,
        @PathVariable("packageName") String packageName,
        HttpServletResponse response) {
        return entryService.findEntry(entryPublicId)
            .flatMap(e ->
                taskService.findTask(entryPublicId, taskName)
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

    @GetMapping(path = "/magiclink/{publicId}")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<MagicTokenResponse> generateMagicToken(@PathVariable("publicId") String publicId) {
        String encrypted = encryptionService.encrypt(ImmutableMagicToken.of(publicId));
        return ResponseEntity.ok(ImmutableMagicTokenResponse.of(encrypted));
    }

    @GetMapping(path = "/admin/data-delivery")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<List<Resource<CompanyLatestEntry>>> fetchLatestEntriesPerCompany() {
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService
            .getDataDeliveryOverview(
                meService.isAdmin()
                    ? null
                    : meService.findCompanies());
        return ResponseEntity.ok(Streams.collect(companyLatestEntries, this::asCompanyLatestEntryResource));
    }

    @GetMapping(path = "/admin/data-delivery/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<StreamingResponseBody> exportDataDeliveryOverview(@RequestParam(name = "language") String language) {
        StreamingResponseBody stream = outputStream -> {
            adminToolsService.exportDataDeliveryToCsv(outputStream, language);
        };

        String filename = "dataDelivery_"+ LocalDateTime.now() +".csv";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s", filename))
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .body(stream);
    }

    /**
     * @deprecated This endpoint is used by multiple pages which is against our general UI API design. It is not
     *             outright removed as refactoring everything would be too costly, however please don't reuse UI
     *             endpoints in the future.
     */
    @Deprecated(since = "2024-09-24")
    @GetMapping(path = "/admin/entries")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<List<Resource<Entry>>> listCompanyEntries(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            List<Entry> entries = queueHandlerService.getAllQueueEntriesFor(businessId);
            return ResponseEntity.ok(Streams.collect(entries, e -> asEntryStateResource(e, e.publicId())));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping(path = "/pages/admin-company-entries/{businessId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<CompanyEntriesPage>> companyEntriesPage(@PathVariable(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(Resource.resource(uiService.companyEntriesPage(businessId)));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping(path = "/admin/companies/{businessId}/info")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<ImmutableCompanyInfo>> getCompanyInfo(@PathVariable("businessId") String businessId) {
        return companyHierarchyService.findByBusinessId(businessId)
            .filter(meService::isAllowedToAccess)
            .map(company -> ResponseEntity.ok(new Resource<>(ImmutableCompanyInfo.builder()
                .company(company)
                .contexts(contextService.findByBusinessId(businessId))
                .hierarchies(companyHierarchyService.getHierarchiesContainingCompany(businessId))
                .rulesets(rulesetService.selectRulesets(businessId))
                .build(), null, Map.of()))
            ).orElseGet(() ->
                Responses.notFound((String.format("Company with business id %s either does not exist or not authorized to be accessed", businessId))));
    }

    @GetMapping(path = "/admin/companies/hierarchy")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<Hierarchy>>> getHierarchies(@RequestParam("businessId") String businessId) {
        return ResponseEntity.ok(new Resource<>(companyHierarchyService.getHierarchies(businessId), null, null));
    }

    @PutMapping(path = "/admin/companies/{businessId}")
    @JsonView(DataVisibility.AdminRestricted.class)
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
    @JsonView(DataVisibility.AdminRestricted.class)
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
    @JsonView(DataVisibility.AdminRestricted.class)
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
    @JsonView(DataVisibility.AdminRestricted.class)
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
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAnyAuthority('vaco.admin', 'vaco.company_admin')")
    public ResponseEntity<Resource<List<CompanyWithFormatSummary>>> listCompanies() {
        return ResponseEntity.ok(new Resource<>(adminToolsService.getCompaniesWithFormatInfos(), null, null));
    }

    private Resource<CompanyLatestEntry> asCompanyLatestEntryResource(CompanyLatestEntry companyLatestEntry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        Map<String, Link> refs = new HashMap<>();
        if (companyLatestEntry.publicId() != null) {
            refs.put("badge", Link.to(
                vacoProperties.baseUrl(),
                RequestMethod.GET,
                ignored -> fromMethodCall(on(BadgeController.class).entryBadge(companyLatestEntry.publicId(), null))));
        }
        links.put("refs", refs);
        return new Resource<>(companyLatestEntry, null, links);
    }

    private <T> Resource<T> asEntryStateResource(T data, String publicId) {
        Map<String, Map<String, Link>> links = Map.of(
            "refs",
            Map.of(
                "self",
                Link.to(
                    vacoProperties.baseUrl(),
                    RequestMethod.GET,
                    ignored -> fromMethodCall(on(UiController.class).fetchEntryState(publicId, null))),
                "badge",
                Link.to(
                    vacoProperties.baseUrl(),
                    RequestMethod.GET,
                    ignored -> fromMethodCall(on(BadgeController.class).entryBadge(publicId, null))))
        );
        return new Resource<>(data, null, links);
    }

    private Resource<Entry> asQueueHandlerResource(Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", Link.to(
            vacoProperties.baseUrl(),
            RequestMethod.GET,
            ignored -> fromMethodCall(on(QueueController.class).fetchEntry(entry.publicId())))));

        if (entry.packages() != null) {
            ConcurrentMap<String, Map<String, Link>> packageLinks = new ConcurrentHashMap<>();
            entry.packages().forEach(p -> {
                String taskName = p.task().name();
                packageLinks.computeIfAbsent(taskName, t -> new HashMap<>())
                    .put(p.name(),
                        Link.to(
                            vacoProperties.baseUrl(),
                            RequestMethod.GET,
                            ignored -> fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), taskName, p.name(), null))));
            });

            links.putAll(packageLinks);
        }

        return new Resource<>(entry, null, links);
    }
}
