package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static fi.digitraffic.tis.Constants.FINTRAFFIC_BUSINESS_ID;

@Service
public class QueueHandlerService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CachingService cachingService;
    private final MeService meService;
    private final MessagingService messagingService;
    private final CompanyHierarchyService companyHierarchyService;
    private final EntryRepository entryRepository;
    private final EntryRequestMapper entryRequestMapper;

    public QueueHandlerService(CachingService cachingService,
                               MeService meService,
                               EntryRequestMapper entryRequestMapper,
                               MessagingService messagingService,
                               CompanyHierarchyService companyHierarchyService,
                               EntryRepository entryRepository) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.meService = Objects.requireNonNull(meService);
        this.entryRequestMapper = Objects.requireNonNull(entryRequestMapper);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    @Transactional
    public Entry processQueueEntry(EntryRequest entryRequest) {
        Entry converted = entryRequestMapper.toEntry(entryRequest);

        autoregisterCompany(converted.metadata(), converted.businessId());

        Entry result = entryRepository.create(converted);

        logger.debug("Processing done for entry request and new entry created as {}, submitting to delegation", result.publicId());

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(result)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        return cachingService.cacheEntry(
                cachingService.keyForEntry(result.publicId(), true),
                key -> result)
            .get();
    }

    /**
     * Autoregister referenced company if metadata contains necessary information and originates from a reliable
     * source. Also links the company as {@link fi.digitraffic.tis.vaco.company.model.Partnership} under Fintraffic.
     *
     * @param metadata Entry metadata, if available
     * @param businessId Business id of the company to potentially create.
     */
    private void autoregisterCompany(JsonNode metadata, String businessId) {
        if (metadata != null && metadata.has("caller") && metadata.has("operator-name")) {
            JsonNode caller = metadata.get("caller");
            JsonNode operatorName = metadata.get("operator-name");

            String callerName = caller.asText();
            if ("FINAP".equals(callerName)) {
                String finapOperator = operatorName.asText();
                ImmutableCompany operatorCompany = ImmutableCompany.of(businessId, finapOperator);
                Optional<Company> createdCompany = companyHierarchyService.createCompany(operatorCompany);

                createdCompany.ifPresent(newOperator -> {
                    logger.info("New company registration from FINAP: {} / {}", businessId, finapOperator);

                    Optional<Company> fintrafficOrg = companyHierarchyService.findByBusinessId(FINTRAFFIC_BUSINESS_ID);
                    fintrafficOrg.ifPresent(fintraffic -> {
                        logger.debug("Registering partnership between Fintraffic ({}) and FINAP originated operator {} / {}", fintraffic.businessId(), businessId, finapOperator);
                        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, fintraffic, newOperator);
                    });
                });
            } else {
                logger.debug("Unrecognized caller '{}', will not autoregister new company", callerName);
            }
        } else {
            logger.debug("Metadata doesn't contain usable caller info, will not autoregister calling company");
        }
    }

    public Optional<Entry> findEntry(String publicId) {
        return findEntry(publicId, false);
    }

    public Optional<Entry> findEntry(String publicId, boolean skipErrorsField) {
        return entryRepository.findByPublicId(publicId, skipErrorsField)
            .map(e -> {
                cachingService.invalidateEntry(e);
                return e;
            });
    }

    public Entry getEntry(String publicId, boolean skipErrorsField) {
        return cachingService.cacheEntry(
            cachingService.keyForEntry(publicId, skipErrorsField),
            key -> entryRepository.findByPublicId(publicId, skipErrorsField)
                .orElseThrow(() -> new UnknownEntityException(publicId, "Entry not found"))
        ).get();
    }

    public List<Entry> getAllQueueEntriesFor(String businessId, boolean full) {
        List<Entry> entries = entryRepository.findAllByBusinessId(businessId, full);
        entries.forEach(entry -> cachingService.cacheEntry(
            cachingService.keyForEntry(entry.publicId(), full),
            key -> entry));
        return entries;
    }

    public List<Entry> getAllEntriesVisibleForCurrentUser(boolean full) {
        Set<String> allAccessibleBusinessIds = new HashSet<>();
        meService.findCompanies().forEach(company ->
            allAccessibleBusinessIds.addAll(companyHierarchyService.listAllChildren(company).keySet()));

        List<Entry> entries = entryRepository.findAllForBusinessIds(allAccessibleBusinessIds, full);
        entries.forEach(entry -> cachingService.cacheEntry(
            cachingService.keyForEntry(entry.publicId(), full),
            key -> entry));
        return entries;
    }
}
