package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.ConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.ValidationInputRecord;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.ConversionInputRepository;
import fi.digitraffic.tis.vaco.db.repositories.ValidationInputRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static fi.digitraffic.tis.Constants.FINTRAFFIC_BUSINESS_ID;

@Service
public class QueueHandlerService {

    private final ContextRepository contextRepository;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CachingService cachingService;

    private final MeService meService;

    private final MessagingService messagingService;

    private final CompanyHierarchyService companyHierarchyService;

    private final EntryService entryService;

    private final TaskService taskService;

    private final TransactionTemplate transactionTemplate;

    private final RecordMapper recordMapper;

    private final EntryRepository entryRepository;

    private final ValidationInputRepository validationInputRepository;

    private final ConversionInputRepository conversionInputRepository;

    public QueueHandlerService(CachingService cachingService,
                               MeService meService,
                               EntryService entryService,
                               MessagingService messagingService,
                               CompanyHierarchyService companyHierarchyService,
                               TaskService taskService,
                               TransactionTemplate transactionTemplate,
                               RecordMapper recordMapper,
                               EntryRepository entryRepository,
                               ValidationInputRepository validationInputRepository,
                               ConversionInputRepository conversionInputRepository,
                               ContextRepository contextRepository) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.meService = Objects.requireNonNull(meService);
        this.entryService = Objects.requireNonNull(entryService);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.taskService = Objects.requireNonNull(taskService);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.validationInputRepository = Objects.requireNonNull(validationInputRepository);
        this.conversionInputRepository = Objects.requireNonNull(conversionInputRepository);
        this.contextRepository = Objects.requireNonNull(contextRepository);
    }

    public Optional<Entry> processQueueEntry(Entry entry) {
        Optional<Entry> result = transactionTemplate.execute(status -> {
            autoregisterCompany(entry.metadata(), entry.businessId());

            Optional<ContextRecord> context = Optional.ofNullable(entry.context())
                .flatMap(ctx -> contextRepository.upsert(entry));

            return entryRepository.create(context, entry)
                .map(persisted -> {
                    ImmutableEntry.Builder resultBuilder = recordMapper.toEntryBuilder(persisted, context);

                    List<ValidationInputRecord> validationInputs = validationInputRepository.create(persisted, entry.validations());
                    List<ConversionInputRecord> conversionInputs = conversionInputRepository.create(persisted, entry.conversions());

                    return resultBuilder.validations(Streams.collect(validationInputs, recordMapper::toValidationInput))
                        .conversions(Streams.collect(conversionInputs, recordMapper::toConversionInput))
                        // NOTE: createTasks requires validations and conversions to exist at this point
                        .tasks(taskService.createTasks(persisted))
                        .build();
                });
        });

        if (result == null) {
            logger.error("Insert transaction failed for entry {}! Check logs for more information.", entry);
            return Optional.empty();
        } else {
            return result.map(createdEntry -> {
                logger.debug("Processing done for entry request and new entry created as {}, submitting to delegation", createdEntry.publicId());

                ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
                    .entry(createdEntry)
                    .retryStatistics(ImmutableRetryStatistics.of(5))
                    .build();
                messagingService.submitProcessingJob(job);

                return createdEntry;
            });
        }
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
                ImmutableCompany operatorCompany = ImmutableCompany.of(businessId, finapOperator, true);
                if (metadata.has("contact-email")) {
                    operatorCompany = operatorCompany.withContactEmails(metadata.get("contact-email").asText());
                }
                Optional<Company> createdCompany = companyHierarchyService.createCompany(operatorCompany);

                if (createdCompany.isPresent()) {
                    logger.info("New company registration from FINAP: {} / {}", businessId, finapOperator);

                    Company newOperator = createdCompany.get();
                    Optional<Company> fintrafficOrg = companyHierarchyService.findByBusinessId(FINTRAFFIC_BUSINESS_ID);
                    fintrafficOrg.ifPresent(fintraffic -> {
                        logger.debug("Registering partnership between Fintraffic ({}) and FINAP originated operator {} / {}", fintraffic.businessId(), businessId, finapOperator);
                        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, fintraffic, newOperator);
                    });
                } else {
                    companyHierarchyService.findByBusinessId(businessId).ifPresent(existingOperator -> {
                        if (metadata.has("contact-email")
                            && existingOperator.contactEmails().isEmpty()) {
                            logger.info("Updating {}'s ({}) contact emails to be the one originating from FINAP", existingOperator.name(), existingOperator.businessId());
                            companyHierarchyService.updateContactEmails(existingOperator, List.of(metadata.get("contact-email").asText()));
                        }
                    });
                }
            } else {
                logger.debug("Unrecognized caller '{}', will not autoregister new company", callerName);
            }
        } else {
            logger.debug("Metadata doesn't contain usable caller info, will not autoregister calling company");
        }
    }

    public Entry getEntry(String publicId) {
        return entryService.findEntry(publicId)
            .orElseThrow(() -> new UnknownEntityException(publicId, "Entry not found"));
    }

    public List<Entry> getAllQueueEntriesFor(String businessId) {
        List<Entry> entries = entryService.findAllByBusinessId(businessId);
        entries.forEach(entry -> cachingService.cacheEntry(
            entry.publicId(),
            key -> entry));
        return entries;
    }

}
