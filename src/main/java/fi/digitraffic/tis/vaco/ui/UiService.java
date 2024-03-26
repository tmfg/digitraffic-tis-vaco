package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMyDataEntrySummary;
import fi.digitraffic.tis.vaco.ui.model.MyDataEntrySummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UiService {

    private final MeService meService;
    private final CompanyHierarchyService companyHierarchyService;
    private final CachingService cachingService;
    private final EntryRepository entryRepository;

    public UiService(MeService meService, CompanyHierarchyService companyHierarchyService, CachingService cachingService, EntryRepository entryRepository) {
        this.meService = meService;
        this.companyHierarchyService = companyHierarchyService;
        this.cachingService = cachingService;
        this.entryRepository = entryRepository;
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


    private MyDataEntrySummary convertToSummary(PersistentEntry pe) {
        return ImmutableMyDataEntrySummary.of(pe.publicId(), pe.name(), pe.format(), pe.status())
            .withCreated(pe.created())
            .withStarted(pe.started())
            .withUpdated(pe.updated())
            .withCompleted(pe.completed());
    }
}
