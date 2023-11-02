package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
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

    private final OrganizationService organizationService;

    private final QueueHandlerRepository queueHandlerRepository;

    private final EntryRequestMapper entryRequestMapper;
    private final CooperationService cooperationService;

    public QueueHandlerService(EntryRequestMapper entryRequestMapper,
                               MessagingService messagingService,
                               OrganizationService organizationService,
                               QueueHandlerRepository queueHandlerRepository,
                               CooperationService cooperationService) {
        this.entryRequestMapper = entryRequestMapper;
        this.messagingService = messagingService;
        this.organizationService = organizationService;
        this.queueHandlerRepository = queueHandlerRepository;
        this.cooperationService = cooperationService;
    }

    @Transactional
    public Entry processQueueEntry(EntryRequest entryRequest) {
        Entry converted = entryRequestMapper.toEntry(entryRequest);

        autoregisterOrganization(converted.metadata(), converted.businessId());

        Entry result = queueHandlerRepository.create(converted);

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(result)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        return result;
    }

    /**
     * Autoregister referenced organization if metadata contains necessary information and originates from a reliable
     * source. Also links the organization as cooperation under Fintraffic.
     *
     * @param metadata Entry metadata, if available
     * @param businessId Business id of the organization to potentially create.
     */
    private void autoregisterOrganization(JsonNode metadata, String businessId) {
        if (metadata != null && metadata.has("caller") && metadata.has("operator-name")) {
            JsonNode caller = metadata.get("caller");
            JsonNode operatorName = metadata.get("operator-name");

            String callerName = caller.asText();
            if ("FINAP".equals(callerName)) {
                String finapOperator = operatorName.asText();
                ImmutableOrganization operatorOrganization = ImmutableOrganization.of(businessId, finapOperator);
                Optional<Organization> createdOrganization = organizationService.createOrganization(operatorOrganization);

                createdOrganization.ifPresent(newOperator -> {
                    logger.info("New organization registration from FINAP: {} / {}", businessId, finapOperator);

                    Optional<Organization> fintrafficOrg = organizationService.findByBusinessId(FINTRAFFIC_BUSINESS_ID);
                    fintrafficOrg.ifPresent(fintraffic -> {
                        logger.debug("Registering cooperation between Fintraffic ({}) and FINAP originated operator {} / {}", fintraffic.businessId(), businessId, finapOperator);
                        cooperationService.create(CooperationType.AUTHORITY_PROVIDER, fintraffic, newOperator);
                    });
                });
            } else {
                logger.debug("Unrecognized caller '{}', will not autoregister new organization", callerName);
            }
        } else {
            logger.debug("Metadata doesn't contain usable caller info, will not autoregister calling organization");
        }
    }

    public Optional<Entry> getEntry(String publicId) {
        return queueHandlerRepository.findByPublicId(publicId);
    }

    public List<ImmutableEntry> getAllQueueEntriesFor(String businessId, boolean full) {
        return queueHandlerRepository.findAllByBusinessId(businessId, full);
    }
}
