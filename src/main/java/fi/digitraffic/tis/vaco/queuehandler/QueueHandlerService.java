package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import fi.digitraffic.tis.vaco.company.service.PartnershipService;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static fi.digitraffic.tis.Constants.FINTRAFFIC_BUSINESS_ID;

@Service
public class QueueHandlerService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;
    private final CompanyService companyService;
    private final QueueHandlerRepository queueHandlerRepository;
    private final EntryRequestMapper entryRequestMapper;
    private final PartnershipService partnershipService;

    public QueueHandlerService(EntryRequestMapper entryRequestMapper,
                               MessagingService messagingService,
                               CompanyService companyService,
                               QueueHandlerRepository queueHandlerRepository,
                               PartnershipService partnershipService) {
        this.entryRequestMapper = entryRequestMapper;
        this.messagingService = messagingService;
        this.companyService = companyService;
        this.queueHandlerRepository = queueHandlerRepository;
        this.partnershipService = partnershipService;
    }

    @Transactional
    public Entry processQueueEntry(EntryRequest entryRequest) {
        Entry converted = entryRequestMapper.toEntry(entryRequest);

        autoregisterCompany(converted.metadata(), converted.businessId());

        Entry result = queueHandlerRepository.create(converted);

        logger.debug("Processing done for entry request and new entry created as {}, submitting to delegation", result.publicId());

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(result)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        return result;
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
                Optional<Company> createdCompany = companyService.createCompany(operatorCompany);

                createdCompany.ifPresent(newOperator -> {
                    logger.info("New company registration from FINAP: {} / {}", businessId, finapOperator);

                    Optional<Company> fintrafficOrg = companyService.findByBusinessId(FINTRAFFIC_BUSINESS_ID);
                    fintrafficOrg.ifPresent(fintraffic -> {
                        logger.debug("Registering partnership between Fintraffic ({}) and FINAP originated operator {} / {}", fintraffic.businessId(), businessId, finapOperator);
                        partnershipService.create(PartnershipType.AUTHORITY_PROVIDER, fintraffic, newOperator);
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
        return queueHandlerRepository.findByPublicId(publicId, false);
    }

    public Optional<Entry> findEntry(String publicId, boolean skipErrorsField) {
        return queueHandlerRepository.findByPublicId(publicId, skipErrorsField);
    }

    public Entry getEntry(String publicId, boolean skipErrorsField) {
        return queueHandlerRepository.findByPublicId(publicId, skipErrorsField)
            .orElseThrow(() -> new UnknownEntityException(publicId, "Entry not found"));
    }

    public List<Entry> getAllQueueEntriesFor(String businessId, boolean full) {
        return queueHandlerRepository.findAllByBusinessId(businessId, full);
    }
}
