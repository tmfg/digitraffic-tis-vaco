package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingRepository;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.mapper.PersistentEntryMapper;
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

import java.util.List;
import java.util.Random;

import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EntryServiceTests {

    private EntryService entryService;
    @Mock
    private EntryRepository entryRepository;
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

    private ImmutableEntry entry;

    // InOrder enables reusing simple verifications in order
    private InOrder inOrderRepository;
    private InOrder inOrderCaching;
    private PersistentEntryMapper persistentEntryMapper = new PersistentEntryMapper();

    @BeforeEach
    void setUp() {
        entryService = new EntryService(
            entryRepository,
            findingRepository,
            cachingService,
            taskService,
            packagesService,
            persistentEntryMapper);
        entry = ImmutableEntry.copyOf(TestObjects.anEntry().build());
        inOrderRepository = Mockito.inOrder(entryRepository);
        inOrderCaching = Mockito.inOrder(cachingService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(entryRepository, taskService, findingRepository, cachingService, queueHandlerService);
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
        thenEntryIsMarkedAs(Status.FAILED);
        thenCacheIsInvalidated();
    }

    private void givenTaskInStatus(Status status) {
        Task failed = ImmutableTask.of(new Random().nextLong(), "failed", 100).withStatus(status);
        BDDMockito.given(taskService.findTasks(entry)).willReturn(List.of(failed));
    }

    private void thenEntryIsMarkedAs(Status status) {
        entryService.updateStatus(entry);
        inOrderRepository.verify(entryRepository).markStatus(entry, status);
    }

    private void thenCacheIsInvalidated() {
        inOrderCaching.verify(cachingService).invalidateEntry(entry);
    }
}
