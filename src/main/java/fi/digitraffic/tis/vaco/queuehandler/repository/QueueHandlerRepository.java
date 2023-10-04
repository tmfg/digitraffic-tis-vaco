package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerRepository;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class QueueHandlerRepository {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ErrorHandlerRepository errorHandlerRepository;
    private final TaskService taskService;
    private final ConversionService conversionService;
    private final PackagesService packagesService;

    public QueueHandlerRepository(JdbcTemplate jdbc,
                                  ObjectMapper objectMapper,
                                  ErrorHandlerRepository errorHandlerRepository,
                                  TaskService taskService,
                                  ConversionService conversionService,
                                  PackagesService packagesService) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.errorHandlerRepository = Objects.requireNonNull(errorHandlerRepository);
        this.taskService = Objects.requireNonNull(taskService);
        this.conversionService = Objects.requireNonNull(conversionService);
        this.packagesService = packagesService;
    }

    @Transactional
    public ImmutableEntry create(Entry entry) {
        ImmutableEntry created = createEntry(entry);
        created = created
            .withValidations(createValidationInputs(created.id(), entry.validations()))
            .withConversions(createConversionInputs(created.id(), entry.conversions()));
        // createTasks requires validations and conversions to exist at this point
        return created.withTasks(createTasks(created));
    }

    private ImmutableEntry createEntry(Entry entry) {
        return jdbc.queryForObject("""
                INSERT INTO entry(business_id, format, url, etag, metadata)
                     VALUES (?, ?, ?, ?, ?)
                  RETURNING id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed
                """,
            RowMappers.QUEUE_ENTRY.apply(objectMapper),
            entry.businessId(), entry.format(), entry.url(), entry.etag(), RowMappers.writeJson(objectMapper, entry.metadata()));
    }

    /**
     * Resolves which tasks should be executed for given entry based on requested validations and configurations.
     * @param entry
     * @return
     */
    private List<ImmutableTask> createTasks(ImmutableEntry entry) {
        List<ImmutableTask> allTasks = new ArrayList<>();

        if (entry.conversions() != null && !entry.conversions().isEmpty()) {
            List<String> conversionTasks = ConversionService.ALL_SUBTASKS;
            allTasks.addAll(extracted(conversionTasks, entry, TaskCategory.CONVERSION));
        }

        // validation tasks are always included
        List<String> validationTasks = ValidationService.ALL_SUBTASKS;
        allTasks.addAll(extracted(validationTasks, entry, TaskCategory.VALIDATION));

        // create task for each rule by rule name
        List<String> rules = new ArrayList<>();
        rules.addAll(entry.validations().stream().map(ValidationInput::name).toList());
        rules.addAll(entry.conversions().stream().map(ConversionInput::name).toList());
        allTasks.addAll(extracted(rules, entry, TaskCategory.RULE));

        // TODO: check return value
        taskService.createTasks(allTasks);

        return taskService.findTasks(entry);
    }

    private static List<ImmutableTask> extracted(List<String> validationTasks, ImmutableEntry entry, TaskCategory category) {
        return Streams.mapIndexed(validationTasks, (i, t) -> ImmutableTask.of(entry.id(), t, category.priority * 100 + i))
            .toList();
    }

    private List<ImmutableValidationInput> createValidationInputs(Long entryId, List<ValidationInput> validations) {
        if (validations == null) {
            return List.of();
        }
        return Streams.map(validations, validation -> jdbc.queryForObject(
                "INSERT INTO validation_input (entry_id, name, config) VALUES (?, ?, ?) RETURNING id, entry_id, name, config",
                RowMappers.VALIDATION_INPUT.apply(objectMapper),
                entryId, validation.name(), RowMappers.writeJson(objectMapper, validation.config())))
            .toList();
    }

    private List<ImmutableConversionInput> createConversionInputs(Long entryId, List<ConversionInput> conversions) {
        if (conversions == null) {
            return List.of();
        }
        return Streams.map(conversions, validation -> jdbc.queryForObject(
                "INSERT INTO conversion_input (entry_id, name, config) VALUES (?, ?, ?) RETURNING id, entry_id, name, config",
                RowMappers.CONVERSION_INPUT.apply(objectMapper),
                entryId, validation.name(), RowMappers.writeJson(objectMapper, validation.config())))
            .toList();
    }

    @Transactional
    public Optional<ImmutableEntry> findByPublicId(String publicId) {
        return findEntry(publicId).map(this::buildCompleteEntry);
    }

    private Optional<ImmutableEntry> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                        SELECT id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed
                          FROM entry qe
                         WHERE qe.public_id = ?
                        """,
                        RowMappers.QUEUE_ENTRY.apply(objectMapper),
                        publicId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    private List<ImmutableValidationInput> findValidationInputs(Long entryId) {
        return jdbc.query("SELECT * FROM validation_input qvi WHERE qvi.entry_id = ?",
            RowMappers.VALIDATION_INPUT.apply(objectMapper),
            entryId);
    }

    private List<ImmutableConversionInput> findConversionInputs(Long entryId) {
        return jdbc.query("SELECT * FROM conversion_input qci WHERE qci.entry_id = ?",
            RowMappers.CONVERSION_INPUT.apply(objectMapper),
            entryId);
    }

    public void startEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET started=NOW(),
                       updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void updateEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void completeEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW(),
                       completed=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public List<ImmutableEntry> findAllByBusinessId(String businessId, boolean full) {
        try {
            List<ImmutableEntry> entries = jdbc.query("""
                    SELECT *
                      FROM entry
                     WHERE business_id = ?
                    """,
                RowMappers.QUEUE_ENTRY.apply(objectMapper),
                businessId);

            if (full) {
                return Streams.map(entries, this::buildCompleteEntry).toList();
            } else {
                return entries;
            }
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    /**
     * Call this to complete {@link Entry} object's fields if needed.
     *
     * @param entry Entry to complete.
     * @return Fully completed entry.
     */
    private ImmutableEntry buildCompleteEntry(ImmutableEntry entry) {
        return entry
            .withTasks(taskService.findTasks(entry))
            .withValidations(findValidationInputs(entry.id()))
            .withConversions(findConversionInputs(entry.id()))
            .withErrors(errorHandlerRepository.findErrorsByEntryId(entry.id()))
            .withPackages(packagesService.findPackages(entry.id()));
    }

}
