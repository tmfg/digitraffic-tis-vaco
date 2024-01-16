package fi.digitraffic.tis.vaco.entries;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingRepository;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class EntryRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FindingRepository findingRepository;
    private final TaskService taskService;
    private final PackagesService packagesService;

    public EntryRepository(JdbcTemplate jdbc,
                           ObjectMapper objectMapper,
                           FindingRepository findingRepository,
                           TaskService taskService,
                           PackagesService packagesService) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.findingRepository = Objects.requireNonNull(findingRepository);
        this.taskService = Objects.requireNonNull(taskService);
        this.packagesService = Objects.requireNonNull(packagesService);
    }

    @Transactional
    public Entry create(Entry entry) {
        Entry baseValues = createEntry(entry);
        ImmutableEntry withRules = ImmutableEntry.copyOf(baseValues)
            .withValidations(createValidationInputs(baseValues.id(), entry.validations()))
            .withConversions(createConversionInputs(baseValues.id(), entry.conversions()));
        // createTasks requires validations and conversions to exist at this point
        return withRules.withTasks(taskService.createTasks(withRules));
    }

    private Entry createEntry(Entry entry) {
        return jdbc.queryForObject("""
                INSERT INTO entry(business_id, format, url, etag, metadata, name, notifications)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                  RETURNING id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed, name, notifications, status
                """,
            RowMappers.ENTRY.apply(objectMapper),
            entry.businessId(),
            entry.format(),
            entry.url(),
            entry.etag(),
            RowMappers.writeJson(objectMapper, entry.metadata()),
            entry.name(),
            ArraySqlValue.create(entry.notifications().toArray(new String[0])));
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
    public Optional<Entry> findByPublicId(String publicId, boolean skipErrors) {
        return findEntry(publicId).map(e -> buildCompleteEntry(e, skipErrors));
    }

    private Optional<Entry> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                        SELECT id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed, name, notifications, status
                          FROM entry qe
                         WHERE qe.public_id = ?
                        """,
                        RowMappers.ENTRY.apply(objectMapper),
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

    public void markStatus(Entry entry, Status status) {
        jdbc.update("""
               UPDATE entry
                  SET status = (?)::status
                WHERE id = ?
            """,
            status.fieldName(),
            entry.id());
    }

    public List<Entry> findAllByBusinessId(String businessId, boolean full) {
        try {
            List<Entry> entries = jdbc.query("""
                    SELECT *
                      FROM entry
                     WHERE business_id = ?
                     ORDER BY created DESC
                    """,
                RowMappers.ENTRY.apply(objectMapper),
                businessId);

            if (full) {
                return Streams.map(entries, e -> buildCompleteEntry(e, true)).toList();
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
    private Entry buildCompleteEntry(Entry entry, boolean skipErrors) {
        List<Task> tasks = taskService.findTasks(entry);
        List<Package> packages = Streams.flatten(tasks, packagesService::findPackages).toList();
        ImmutableEntry e = ImmutableEntry.copyOf(entry)
            .withTasks(tasks)
            .withValidations(findValidationInputs(entry.id()))
            .withConversions(findConversionInputs(entry.id()))
            .withPackages(packages);
        if (!skipErrors) {
            e = e.withFindings(findingRepository.findFindingsByEntryId(entry.id()));
        }
        return e;
    }

    @Transactional
    public Entry reload(Entry entry) {
        return findByPublicId(entry.publicId(), true)
            .orElseThrow(() -> new RuleExecutionException("Failed to reload entry with public id " + entry.publicId() + " from database, corrupt entry?"));
    }
}
