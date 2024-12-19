package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ui.mapper.UiModelMapper;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMyDataEntrySummary;
import fi.digitraffic.tis.vaco.ui.model.MyDataEntrySummary;
import fi.digitraffic.tis.vaco.ui.model.pages.CompanyEntriesPage;
import fi.digitraffic.tis.vaco.ui.model.pages.EntrySummary;
import fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class UiService {

    private final MeService meService;

    private final CompanyHierarchyService companyHierarchyService;

    private final CachingService cachingService;

    private final EntryRepository entryRepository;

    private final ContextRepository contextRepository;

    private final UiModelMapper uiModelMapper;

    public UiService(MeService meService,
                     CompanyHierarchyService companyHierarchyService,
                     CachingService cachingService,
                     EntryRepository entryRepository,
                     ContextRepository contextRepository,
                     UiModelMapper uiModelMapper) {
        this.meService = Objects.requireNonNull(meService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.uiModelMapper = Objects.requireNonNull(uiModelMapper);
    }

    public List<MyDataEntrySummary> getAllEntriesVisibleForCurrentUser() {
        Set<String> allAccessibleBusinessIds = new HashSet<>();
        meService.findCompanies().forEach(company ->
            allAccessibleBusinessIds.addAll(companyHierarchyService.listAllChildren(company).keySet()));

        List<MyDataEntrySummary> allSummaries = new ArrayList<>();
        for (String businessId : allAccessibleBusinessIds) {
            cachingService.cacheEntrySummaries(businessId, k -> Streams.collect(
                entryRepository.findLatestForBusinessId(businessId),
                this::convertToSummary
            )).ifPresent(allSummaries::addAll);
        }
        allSummaries.sort(Comparator.comparing(MyDataEntrySummary::created).reversed());
        return allSummaries;
    }


    private MyDataEntrySummary convertToSummary(EntryRecord pe) {
        Optional<ContextRecord> context = contextRepository.find(pe);
        return ImmutableMyDataEntrySummary.of(pe.publicId(), pe.name(), pe.format(), pe.status())
            .withContext(context.map(ContextRecord::context).orElse(null))
            .withCreated(pe.created())
            .withStarted(pe.started())
            .withUpdated(pe.updated())
            .withCompleted(pe.completed());
    }

    public CompanyEntriesPage companyEntriesPage(String businessId) {
        List<EntrySummary> entries = Streams.collect(
            entryRepository.findAllByBusinessId(businessId),
            r -> uiModelMapper.toEntrySummary(r, contextRepository.find(r)));

        return ImmutableCompanyEntriesPage.builder()
            .addAllEntries(entries)
            .build();
    }
}
