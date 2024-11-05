package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.SubscriptionsRepository;
import fi.digitraffic.tis.vaco.notifications.model.Subscription;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class NotificationsService {

    private final CompanyRepository companyRepository;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EntryRepository entryRepository;

    private final List<Notifier> notifiers;
    private final RecordMapper recordMapper;

    private final SubscriptionsRepository subscriptionsRepository;

    public NotificationsService(List<Notifier> notifiers,
                                EntryRepository entryRepository,
                                SubscriptionsRepository subscriptionsRepository, RecordMapper recordMapper, CompanyRepository companyRepository) {
        this.notifiers = Objects.requireNonNull(notifiers);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.subscriptionsRepository = Objects.requireNonNull(subscriptionsRepository);
        this.recordMapper = recordMapper;
        this.companyRepository = companyRepository;
    }

    public void notifyEntryComplete(Entry entry) {
        Optional<EntryRecord> record = entryRepository.findByPublicId(entry.publicId());
        if (record.isPresent()) {
            for (Notifier notifier : notifiers) {
                try {
                    notifier.notifyEntryComplete(record.get());
                } catch (Exception e) {
                    logger.warn("Failed to send EntryComplete notification of {} with {}", entry.publicId(), notifier, e);
                }
            }
        }
    }

    public Optional<Subscription> subscribe(SubscriptionType type, String subscriber, String resource) {
        Optional<CompanyRecord> subscriberRef = companyRepository.findByBusinessId(subscriber);
        Optional<CompanyRecord> resourceRef = companyRepository.findByBusinessId(resource);

        if (subscriberRef.isPresent() && resourceRef.isPresent()) {
            return subscriptionsRepository.createSubscription(type, subscriberRef.get(), resourceRef.get())
                .map(sub -> recordMapper.toSubscription(sub, subscriberRef.get(), resourceRef.get()))
                .or(Optional::empty);
        } else {
            return Optional.empty();
        }
    }

    public List<Subscription> listSubscriptions(String businessId) {
        Optional<CompanyRecord> subscriberRef = companyRepository.findByBusinessId(businessId);
        if (subscriberRef.isPresent()) {
            CompanyRecord subscriber = subscriberRef.get();
            return Streams.collect(
                subscriptionsRepository.findSubscriptions(subscriber),
                subscriptionRecord -> {
                    CompanyRecord resource = companyRepository.findById(subscriptionRecord.resourceId());
                    return recordMapper.toSubscription(subscriptionRecord, subscriberRef.get(), resource);
                });
        } else {
            return List.of();
        }
    }

    public boolean deleteSubscription(String publicId) {
        return subscriptionsRepository.deleteByPublicId(publicId);
    }
}
