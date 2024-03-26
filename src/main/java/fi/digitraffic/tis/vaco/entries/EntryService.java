package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.mapper.PersistentEntryMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class EntryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EntryRepository entryRepository;
    private final CachingService cachingService;
    private final TaskService taskService;
    private final PackagesService packagesService;
    private final PersistentEntryMapper persistentEntryMapper;

    public EntryService(EntryRepository entryRepository,
                        CachingService cachingService,
                        TaskService taskService,
                        PackagesService packagesService,
                        PersistentEntryMapper persistentEntryMapper) {
        this.taskService = Objects.requireNonNull(taskService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.persistentEntryMapper = Objects.requireNonNull(persistentEntryMapper);
    }

    public void markComplete(Entry entry) {
        entryRepository.completeEntryProcessing(entry);
        cachingService.invalidateEntry(entry.publicId());
    }

    public void markStarted(Entry entry) {
        entryRepository.startEntryProcessing(entry);
        entryRepository.markStatus(entry, Status.PROCESSING);
        cachingService.invalidateEntry(entry.publicId());
    }

    public void markUpdated(Entry entry) {
        entryRepository.updateEntryProcessing(entry);
        cachingService.invalidateEntry(entry.publicId());
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
        cachingService.invalidateEntry(entry.publicId());
    }

    private Status resolveStatus(Entry entry) {
        List<Task> tasks = taskService.findTasks(entry);
        if (tasks.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Entry {} has no tasks, resolving entry as cancelled", entry.publicId());
            }
            return Status.CANCELLED;
        }
        for (Task t : tasks) {
            if (Status.FAILED.equals(t.status())
                || Status.CANCELLED.equals(t.status())) {
                return resolvedStatus(entry, t, Status.FAILED);
            }
            if (Status.ERRORS.equals(t.status())) {
                return resolvedStatus(entry, t, Status.ERRORS);
            }
            if (Status.WARNINGS.equals(t.status())) {
                return resolvedStatus(entry, t, Status.WARNINGS);
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("All of entry {}'s tasks are successful, resolving entry as success", entry.publicId());
        }
        return Status.SUCCESS;
    }

    private Status resolvedStatus(Entry entry, Task t, Status status) {
        if (logger.isInfoEnabled()) {
            logger.info("Entry {} has a task with status {}, resolving entry as {}}", entry.publicId(), t.status(), status);
        }
        return status;
    }

    public Entry create(Entry entry) {
        PersistentEntry persisted = entryRepository.create(entry);

        ImmutableEntry.Builder resultBuilder = persistentEntryMapper.toEntryBuilder(persisted);

        List<ValidationInput> validationInputs = entryRepository.createValidationInputs(persisted, entry.validations());
        List<ConversionInput> conversionInputs = entryRepository.createConversionInputs(persisted, entry.conversions());

        return resultBuilder.validations(validationInputs)
            .conversions(conversionInputs)
            // NOTE: createTasks requires validations and conversions to exist at this point
            .tasks(taskService.createTasks(persisted))
            .build();
    }

    public List<Entry> findAllByBusinessId(String businessId) {
        List<PersistentEntry> entries = entryRepository.findAllByBusinessId(businessId);
        return Streams.map(entries, this::buildCompleteEntry).toList();
    }

    /**
     * Call this to complete {@link Entry} object's fields if needed.
     *
     * @param entry Entry to complete.
     * @return Fully completed entry.
     */
    private Entry buildCompleteEntry(PersistentEntry entry) {
        List<Package> packages = Streams.flatten(taskService.findTasks(entry), packagesService::findPackages).toList();
        return persistentEntryMapper.toEntryBuilder(entry)
            .tasks(taskService.findTasks(entry))
            .validations(taskService.findValidationInputs(entry))
            .conversions(taskService.findConversionInputs(entry))
            .packages(packages)
            .build();
    }

    public Optional<Entry> findEntry(String publicId) {
        return cachingService.cacheEntry(publicId, key ->
            entryRepository.findByPublicId(publicId)
                .map(this::buildCompleteEntry)
                .orElse(null));
    }

    // TODO: this is horrible in both concept and practice
    public Entry reload(Entry entry) {
        cachingService.invalidateEntry(entry.publicId());
        return findEntry(entry.publicId()).get();
    }
}
