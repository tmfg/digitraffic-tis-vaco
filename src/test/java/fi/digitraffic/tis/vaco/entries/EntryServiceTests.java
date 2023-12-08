package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
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

import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EntryServiceTests {

    private EntryService entryService;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private ErrorHandlerService errorHandlerService;

    private ImmutableEntry entry;

    // InOrder enables reusing simple verifications in order
    private InOrder inOrderRepository;

    @BeforeEach
    void setUp() {
        entryService = new EntryService(taskService, errorHandlerService, entryRepository);
        entry = ImmutableEntry.copyOf(TestObjects.anEntry().id(99999999L).build());
        inOrderRepository = Mockito.inOrder(entryRepository);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(entryRepository, taskService, errorHandlerService);
    }

    @Test
    void marksEntryAsSuccessByDefault() {
        givenTaskInStatus(Status.SUCCESS);
        givenHasErrors(false);
        thenEntryIsMarkedAs(Status.SUCCESS);
    }

    @Test
    void entryWithErrorsIsMarkedAsErrors() {
        givenTaskInStatus(Status.SUCCESS);
        givenHasErrors(true);
        thenEntryIsMarkedAs(Status.ERRORS);
    }

    private void givenHasErrors(boolean hasErrors) {
        BDDMockito.given(errorHandlerService.hasErrors(entry)).willReturn(hasErrors);
    }

    /**
     * @see <a href="https://finrail.atlassian.net/wiki/spaces/VACO1/pages/2906095722/Entry+and+Task+Statuses">Entry and Task Statuses</a>
     */
    @Test
    void taskStatesGuideEntryStatusSelection() {
        givenTaskInStatus(Status.FAILED);
        thenEntryIsMarkedAs(Status.FAILED);

        givenTaskInStatus(Status.ERRORS);
        thenEntryIsMarkedAs(Status.ERRORS);

        givenTaskInStatus(Status.WARNINGS);
        thenEntryIsMarkedAs(Status.WARNINGS);

        givenTaskInStatus(Status.CANCELLED);
        thenEntryIsMarkedAs(Status.WARNINGS);
    }

    private void givenTaskInStatus(Status status) {
        Task failed = ImmutableTask.of(entry.id(), "failed", 100).withStatus(status);
        BDDMockito.given(taskService.findTasks(entry)).willReturn(List.of(failed));
    }

    private void thenEntryIsMarkedAs(Status status) {
        entryService.updateStatus(entry);
        inOrderRepository.verify(entryRepository).markStatus(entry, status);
    }
}
