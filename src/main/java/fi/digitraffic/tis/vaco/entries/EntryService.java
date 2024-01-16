package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class EntryService {

    private final EntryRepository entryRepository;
    private final CachingService cachingService;
    private final QueueHandlerService queueHandlerService;
    private final TaskService taskService;

    public EntryService(EntryRepository entryRepository,
                        CachingService cachingService,
                        QueueHandlerService queueHandlerService,
                        TaskService taskService) {
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.taskService = Objects.requireNonNull(taskService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public Optional<Status> getStatus(String publicId) {
        return queueHandlerService.findEntry(publicId).map(Entry::status);
    }

    public Optional<Status> getStatus(String publicId, String taskName) {
        return entryRepository.findByPublicId(publicId, true)
            .map(Entry::tasks)
            .flatMap(tasks -> Streams.filter(tasks, t -> t.name().equals(taskName)).findFirst())
            .map(Task::status);
    }

    public void markComplete(Entry entry) {
        entryRepository.completeEntryProcessing(entry);
        cachingService.invalidateEntry(entry);
    }

    public void markStarted(Entry entry) {
        entryRepository.startEntryProcessing(entry);
        entryRepository.markStatus(entry, Status.PROCESSING);
        cachingService.invalidateEntry(entry);
    }

    public void markUpdated(Entry entry) {
        entryRepository.updateEntryProcessing(entry);
        cachingService.invalidateEntry(entry);
    }

    /**
     * Update entry's status based on transitive values from tasks and errors. Should only be called after entry
     * processing is done.
     *
     * @param entry Entry to update
     */
    public void updateStatus(Entry entry) {
        Status status = resolveStatus(entry);
        entryRepository.markStatus(entry, status);
        cachingService.invalidateEntry(entry);
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
        }
        return Status.SUCCESS;
    }
}
