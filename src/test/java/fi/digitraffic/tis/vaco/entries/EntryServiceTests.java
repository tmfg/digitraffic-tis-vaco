package fi.digitraffic.tis.vaco.entries;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.credentials.CredentialsRepository;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EntryServiceTests {

    private EntryService entryService;
    @Mock
    private EntryRepository entryRepository;

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private FindingRepository findingRepository;
    @Mock
    private PackagesService packagesService;
    @Mock
    private CachingService cachingService;
    @Mock
    private QueueHandlerService queueHandlerService;
    @Mock
    private ContextRepository contextRepository;
    @Mock
    private CredentialsRepository credentialsRepository;

    private ImmutableEntry entry;

    // InOrder enables reusing simple verifications in order
    private InOrder inOrderRepository;
    private InOrder inOrderCaching;
    private RecordMapper recordMapper;
    private EntryRecord entryRecord;

    @BeforeEach
    void setUp() {
        recordMapper = new RecordMapper(new ObjectMapper());
        entryService = new EntryService(
            entryRepository,
            cachingService,
            taskService,
            packagesService,
            recordMapper,
            contextRepository,
            taskRepository,
            credentialsRepository);
        entry = ImmutableEntry.copyOf(TestObjects.anEntry().build());
        entryRecord = ImmutableEntryRecord.of(100000L, entry.publicId(), entry.name(), entry.format(), entry.url(), entry.businessId());
        inOrderRepository = Mockito.inOrder(entryRepository);
        inOrderCaching = Mockito.inOrder(cachingService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            entryRepository,
            taskService,
            findingRepository,
            cachingService,
            queueHandlerService,
            contextRepository,
            taskRepository,
            credentialsRepository);
    }

    @Test
    void marksEntryAsSuccessByDefault() {
        givenTaskInStatus(Status.SUCCESS);
        thenEntryIsMarkedAs(Status.SUCCESS);
        thenCacheIsInvalidated();
    }

    /**
     * @see <a href="https://finrail.atlassian.net/wiki/spaces/VACO1/pages/2906095722/Entry+and+Task+Statuses">Entry and Task Statuses</a>
     */
    @Test
    void taskStatesGuideEntryStatusSelection() {
        givenTaskInStatus(Status.FAILED);
        thenEntryIsMarkedAs(Status.FAILED);
        thenCacheIsInvalidated();

        givenTaskInStatus(Status.ERRORS);
        thenEntryIsMarkedAs(Status.ERRORS);
        thenCacheIsInvalidated();

        givenTaskInStatus(Status.WARNINGS);
        thenEntryIsMarkedAs(Status.WARNINGS);
        thenCacheIsInvalidated();

        givenTaskInStatus(Status.CANCELLED);
        thenEntryIsMarkedAs(Status.CANCELLED);
        thenCacheIsInvalidated();
    }

    @Test
    void markEntryWithoutTasksAsCancelled() {
        givenNoTasks();
        thenEntryIsMarkedAs(Status.CANCELLED);
        thenCacheIsInvalidated();
    }

    private void givenNoTasks() {
        BDDMockito.given(taskRepository.findFirstTask(entryRecord)).willReturn(Optional.empty());
    }

    private void givenTaskInStatus(Status status) {
        Task failed = ImmutableTask.of("failed", 100).withStatus(status);
        BDDMockito.given(taskRepository.findFirstTask(entryRecord)).willReturn(Optional.of(failed));
    }

    private void thenEntryIsMarkedAs(Status status) {
        BDDMockito.given(entryRepository.findByPublicId(entry.publicId())).willReturn(Optional.of(entryRecord));
        entryService.updateStatus(entry);
        inOrderRepository.verify(entryRepository).findByPublicId(entry.publicId());
        inOrderRepository.verify(entryRepository).markStatus(entry, status);
    }

    private void thenCacheIsInvalidated() {
        inOrderCaching.verify(cachingService).invalidateEntry(entry.publicId());
    }
}
