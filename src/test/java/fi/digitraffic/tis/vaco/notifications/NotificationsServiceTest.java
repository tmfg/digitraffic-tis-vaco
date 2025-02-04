package fi.digitraffic.tis.vaco.notifications;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.SubscriptionsRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationsServiceTest {

    private ObjectMapper objectMapper;
    @Mock
    private EntryRepository entryRepository;

    @Mock
    private SubscriptionsRepository subscriptionsRepository;

    private RecordMapper recordMapper;

    @Mock
    private CompanyRepository companyRepository;

    private NotificationsService notificationsService;
    private ImmutableEntry entry;
    private ImmutableEntryRecord entryRecord;
    private AtomicReference<EntryRecord> notifiedRecord;

    @BeforeEach
    void setUp() {

        entryRecord = ImmutableEntryRecord.of(
                456789L,
                NanoIdUtils.randomNanoId(),
                "test",
                "gtfs",
                "http://example.fi",
                "12344")
            .withSendNotifications(false);

        objectMapper = new ObjectMapper();
        recordMapper = new RecordMapper(objectMapper);

        entry = recordMapper.toEntryBuilder(entryRecord, Optional.empty(), Optional.empty()).build();

        notifiedRecord = new AtomicReference<>(null);

        List<Notifier> notifiers = List.of(notifiedRecord::set);

        notificationsService = new NotificationsService(notifiers,
            entryRepository,
            subscriptionsRepository,
            recordMapper,
            companyRepository);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(entryRepository, subscriptionsRepository, companyRepository);
    }

    @Test
    void notificationsAreNotSentWhenSendNotificationsIsFalse() {

        when(entryRepository.findByPublicId(entry.publicId())).thenReturn(Optional.of(entryRecord));

        notificationsService.notifyEntryComplete(entry);

        assertThat(notifiedRecord.get(), nullValue());
    }

    @Test
    void notificationsAreSentWhenSendNotificationsIsTrue() {

        entryRecord = entryRecord.withSendNotifications(true);
        when(entryRepository.findByPublicId(entry.publicId())).thenReturn(Optional.of(entryRecord));

        notificationsService.notifyEntryComplete(entry);

        assertThat(notifiedRecord.get(), notNullValue());
    }

}
