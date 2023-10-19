package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
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
    public Entry processQueueEntry(ImmutableEntryRequest entryRequest) {
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

            if ("FINAP".equals(caller.asText())) {
                String finapOperator = operatorName.asText();
                logger.trace("New organization registration from FINAP: {} / {}", businessId, finapOperator);
                ImmutableOrganization operatorOrganization = ImmutableOrganization.of(businessId, finapOperator);
                Optional<ImmutableOrganization> createdOrganization = organizationService.createOrganization(operatorOrganization);

                createdOrganization.ifPresent(newOperator -> {
                    Optional<ImmutableOrganization> fintrafficOrg = organizationService.findByBusinessId(FINTRAFFIC_BUSINESS_ID);
                    fintrafficOrg.ifPresent(fintraffic -> {
                        logger.trace("Registering cooperation between Fintraffic and FINAP originated operator {} / {}", businessId, finapOperator);
                        cooperationService.create(CooperationType.AUTHORITY_PROVIDER, fintraffic, newOperator);
                    });
                });
            }
        }
    }

    public Optional<ImmutableEntry> getEntry(String publicId) {
        return queueHandlerRepository.findByPublicId(publicId);
    }

    public List<ImmutableEntry> getAllQueueEntriesFor(String businessId, boolean full) {
        return queueHandlerRepository.findAllByBusinessId(businessId, full);
    }
}
