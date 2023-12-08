package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class EntryService {
    private final EntryRepository entryRepository;
    private final TaskService taskService;
    private final ErrorHandlerService errorHandlerService;

    public EntryService(TaskService taskService,
                        ErrorHandlerService errorHandlerService,
                        EntryRepository entryRepository) {
        this.taskService = Objects.requireNonNull(taskService);
        this.errorHandlerService = Objects.requireNonNull(errorHandlerService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    public Optional<Status> getStatus(String publicId) {
        return entryRepository.findByPublicId(publicId, false)
            .map(Entry::status);
    }

    public Optional<Status> getStatus(String publicId, String taskName) {
        return entryRepository.findByPublicId(publicId, true)
            .map(Entry::tasks)
            .flatMap(tasks -> Streams.filter(tasks, t -> t.name().equals(taskName)).findFirst())
            .map(Task::status);
    }

    public void markComplete(Entry entry) {
        entryRepository.completeEntryProcessing(entry);
    }

    public void markStarted(Entry entry) {
        entryRepository.startEntryProcessing(entry);
        entryRepository.markStatus(entry, Status.PROCESSING);
    }

    public void markUpdated(Entry entry) {
        entryRepository.updateEntryProcessing(entry);
    }

    /**
     * Update entry's status based on transitive values from tasks and errors.
     * @param entry Entry to update
     */
    public void updateStatus(Entry entry) {
        Status status = resolveStatus(entry);
        entryRepository.markStatus(entry, status);
    }

    private Status resolveStatus(Entry entry) {
        for (Task t : taskService.findTasks(entry)) {
            if (Status.FAILED.equals(t.status())) {
                return Status.FAILED;
            }
            if (Status.ERRORS.equals(t.status())) {
                return Status.ERRORS;
            }
            if (Status.WARNINGS.equals(t.status())) {
                return Status.WARNINGS;
            }
            if (Status.CANCELLED.equals(t.status())) {
                return Status.WARNINGS;
            }
        }
        if (errorHandlerService.hasErrors(entry)) {
            return Status.ERRORS;
        }
        return Status.SUCCESS;
    }
}
