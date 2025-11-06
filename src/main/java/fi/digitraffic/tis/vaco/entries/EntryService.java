package fi.digitraffic.tis.vaco.entries;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.credentials.CredentialsRepository;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

@Service
public class EntryService {

    private final CredentialsRepository credentialsRepository;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EntryRepository entryRepository;
    private final CachingService cachingService;
    private final TaskService taskService;
    private final PackagesService packagesService;
    private final RecordMapper recordMapper;

    private final ContextRepository contextRepository;
    private final TaskRepository taskRepository;

    public EntryService(EntryRepository entryRepository,
                        CachingService cachingService,
                        TaskService taskService,
                        PackagesService packagesService,
                        RecordMapper recordMapper,
                        ContextRepository contextRepository,
                        TaskRepository taskRepository,
                        CredentialsRepository credentialsRepository) {
        this.taskService = Objects.requireNonNull(taskService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.credentialsRepository = Objects.requireNonNull(credentialsRepository);
    }

    public void markComplete(Entry entry) {
        entryRepository.completeEntryProcessing(entry);
        cachingService.invalidateEntry(entry.publicId());
        cachingService.invalidateEntrySummaries(entry.businessId());
    }

    public void markStarted(Entry entry) {
        entryRepository.startEntryProcessing(entry);
        entryRepository.markStatus(entry, Status.PROCESSING);
        cachingService.invalidateEntry(entry.publicId());
        cachingService.invalidateEntrySummaries(entry.businessId());
    }

    public void markUpdated(Entry entry) {
        entryRepository.updateEntryProcessing(entry);
        cachingService.invalidateEntry(entry.publicId());
        cachingService.invalidateEntrySummaries(entry.businessId());
    }

    /**
     * Update entry's status based on transitive values from tasks and errors. Should only be called after entry
     * processing is done.
     *
     * @param entry Entry to update
     */
    public void updateStatus(Entry entry) {
        Optional<EntryRecord> entryRecord = entryRepository.findByPublicId(entry.publicId());
        Status status = resolveStatus(entryRecord);
        entryRepository.markStatus(entry, status);
        cachingService.invalidateEntry(entry.publicId());
    }

    private Status resolveStatus(Optional<EntryRecord> entryRecord) {
        if (entryRecord.isPresent()) {
            EntryRecord entry = entryRecord.get();

            Optional<Task> firstTask = taskRepository.findFirstTask(entry);

            if (firstTask.isPresent()) {
                Task task = firstTask.get();
                if (Status.FAILED.equals(task.status())) {
                    return resolvedStatus(entry, task, Status.FAILED);
                }
                if (Status.CANCELLED.equals(task.status())) {
                    return resolvedStatus(entry, task, Status.CANCELLED);
                }
                if (Status.ERRORS.equals(task.status())) {
                    return resolvedStatus(entry, task, Status.ERRORS);
                }
                if (Status.WARNINGS.equals(task.status())) {
                    return resolvedStatus(entry, task, Status.WARNINGS);
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Entry {} has no tasks, resolving entry as cancelled", entry.publicId());
                }
                return Status.CANCELLED;

            }
            if (logger.isInfoEnabled()) {

                logger.info("All of entry {}'s tasks are successful, resolving entry as success", entry.publicId());
            }
            return Status.SUCCESS;
        } else {
            logger.warn("Entry not present, cannot resolve status - possible data corruption");
            return Status.FAILED;
        }
    }

    private Status resolvedStatus(EntryRecord entryRecord, Task t, Status status) {
        if (logger.isInfoEnabled()) {
            logger.info("Entry {} has a task with status {}, resolving entry as {}}", entryRecord.publicId(), t.status(), status);
        }
        return status;
    }

    /**
     * @deprecated This method should not be called to create entries! Instead create the objects in your service in the
     *             form you need. Don't be afraid of duplication, there won't be much.
     * @param entry Entry to create
     * @return Created entry
     */
    @Deprecated(forRemoval = true)
    public Optional<Entry> create(Entry entry) {
        Optional<ContextRecord> context = Optional.empty();
        Optional<CredentialsRecord> credentials = Optional.empty();
        return entryRepository.create(entry, context, Optional.empty()).map(persisted -> {
            ImmutableEntry.Builder resultBuilder = recordMapper.toEntryBuilder(persisted, context, credentials);

            List<ValidationInput> validationInputs = entryRepository.createValidationInputs(persisted, entry.validations());
            List<ConversionInput> conversionInputs = entryRepository.createConversionInputs(persisted, entry.conversions());

            return resultBuilder.validations(validationInputs)
                .conversions(conversionInputs)
                // NOTE: createTasks requires validations and conversions to exist at this point
                .tasks(taskService.createTasks(persisted))
                .build();
        });
    }

    public List<Entry> findAllByBusinessId(String businessId) {
        return findAllByBusinessId(businessId, OptionalInt.empty(), Optional.empty());
    }

    public List<Entry> findAllByBusinessId(String businessId, OptionalInt count, Optional<String> name) {
        List<EntryRecord> entries = entryRepository.findAllByBusinessId(businessId, count, name);
        return Streams.map(entries, this::buildCompleteEntry).toList();
    }

    /**
     * Call this to complete {@link Entry} object's fields if needed.
     *
     * @param entry Entry to complete.
     * @return Fully completed entry.
     */
    private Entry buildCompleteEntry(EntryRecord entry) {
        List<Task> tasks = taskService.findTasks(entry);
        List<Package> packages = Streams.flatten(tasks, task -> packagesService.findAvailablePackages(task, entry.publicId())).toList();
        Optional<ContextRecord> context = contextRepository.find(entry);
        Optional<CredentialsRecord> credentials = credentialsRepository.findForEntry(entry);
        return recordMapper.toEntryBuilder(entry, context, credentials)
            .tasks(tasks)
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

    public Optional<Entry> findLatestEntryForContext(String businessId, String context) {
        return entryRepository.findLatestForBusinessIdAndContext(businessId, context)
            .map(er -> recordMapper.toEntryBuilder(er, contextRepository.find(er), credentialsRepository.findForEntry(er)).build());
    }

    /**
     * Update given entry's etag to the given one.
     * @param entry Entry to update
     * @param etag New etag.
     * @return Returns entry with updated etag if the update went through to database successfully
     */
    public Entry updateEtag(Entry entry, String etag) {
        if (entryRepository.updateEtag(entry, etag)) {
            ImmutableEntry updated = ImmutableEntry.copyOf(entry).withEtag(etag);
            cachingService.updateEntry(entry.publicId(), updated);
            return updated;
        } else {
            return entry;
        }
    }

    public boolean updateCredentials(Entry entry, Long credentialsId) {
        return entryRepository.updateEntry(entry, credentialsId);
    }
}
