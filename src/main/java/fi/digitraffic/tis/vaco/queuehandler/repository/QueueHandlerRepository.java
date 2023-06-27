package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.validation.ValidationProcessException;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class QueueHandlerRepository {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ErrorHandlerRepository errorHandlerRepository;

    public QueueHandlerRepository(JdbcTemplate jdbc,
                                  ObjectMapper objectMapper,
                                  ErrorHandlerRepository errorHandlerRepository) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.errorHandlerRepository = errorHandlerRepository;
    }

    @Transactional
    public ImmutableQueueEntry create(QueueEntry entry) {
        ImmutableQueueEntry created = createEntry(entry);
        return created.withPhases(createPhases(created.id(), entry.phases()))
                .withValidation(createValidationInput(created.id(), entry.validation()))
                .withConversion(createConversionInput(created.id(), entry.conversion()));
    }

    private ImmutableQueueEntry createEntry(QueueEntry entry) {
        return jdbc.queryForObject("""
                INSERT INTO entry(business_id, format, url, etag, metadata)
                     VALUES (?, ?, ?, ?, ?)
                  RETURNING id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed
                """,
            RowMappers.QUEUE_ENTRY.apply(objectMapper),
            entry.businessId(), entry.format(), entry.url(), entry.etag(), writeJson(entry.metadata()));
    }

    private List<ImmutablePhase> createPhases(Long entryId, List<Phase> phases) {
        if (phases == null) {
            return List.of();
        }
        return phases.stream()
                .map(phase -> jdbc.queryForObject(
                        "INSERT INTO phase(entry_id, name) VALUES (?, ?) RETURNING id, name, started",
                        RowMappers.PHASE,
                        entryId))
                .toList();
    }

    private ValidationInput createValidationInput(Long entryId, ValidationInput validation) {
        return jdbc.queryForObject(
                "INSERT INTO validation_input (entry_id) VALUES (?) RETURNING id, entry_id",
                (rs, rowNum) -> ImmutableValidationInput.builder().build(),
                entryId);
    }

    private ConversionInput createConversionInput(Long entryId, ConversionInput conversion) {
        return conversion != null
            ? jdbc.queryForObject(
                "INSERT INTO conversion_input (entry_id, target_format) VALUES (?, ?) RETURNING id, entry_id, target_format",
                    (rs, rowNum) -> ImmutableConversionInput.builder().build(),
                    entryId, conversion.targetFormat())
            : null;
    }

    @Transactional
    public Optional<ImmutableQueueEntry> findByPublicId(String publicId) {
        return findEntry(publicId).map(this::buildCompleteEntry);
    }

    private Optional<ImmutableQueueEntry> findEntry(String publicId) {
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

    private List<ImmutablePhase> findPhases(Long entryId) {
        return jdbc.query("SELECT * FROM phase qp WHERE qp.entry_id = ?",
                RowMappers.PHASE,
            entryId);
    }

    private ValidationInput findValidationInput(Long entryId) {
        return jdbc.queryForObject("SELECT * FROM validation_input qvi WHERE qvi.entry_id = ?",
            (rs, row) -> null,
            entryId);
    }

    private Optional<ConversionInput> findConversionInput(Long entryId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM conversion_input qci WHERE qci.entry_id = ?",
                (rs, row) -> null,
                entryId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public ImmutablePhase startPhase(ImmutablePhase phase) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO phase (entry_id, name, priority, updated)
                     VALUES (?, ?, ?, NOW())
                  RETURNING id, entry_id, name, priority, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.entryId(), phase.name(), phase.priority());
        } catch (DuplicateKeyException dke) {
            throw new ValidationProcessException("Failed to start phase " + phase + ", did you try to START the same phase twice?", dke);
        }
    }

    public ImmutablePhase updatePhase(ImmutablePhase phase) {
        return jdbc.queryForObject("""
                     UPDATE phase
                        SET updated = NOW()
                      WHERE id = ?
                  RETURNING id, entry_id, name, priority, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.id());

    }

    public ImmutablePhase completePhase(ImmutablePhase phase) {
        return jdbc.queryForObject("""
                     UPDATE phase
                        SET updated = NOW(),
                            completed = NOW()
                      WHERE id = ?
                  RETURNING id, entry_id, name, priority, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.id());
    }

    /**
     * Finds all phases for given entry, if any, ordered by priority.
     * <p>
     * The priority order is somewhat arbitrary and decided during insert.
     *
     * @param entry Entry reference for finding the phases.
     * @return Ordered list of phases or empty list if none found.
     */
    public List<ImmutablePhase> findPhases(QueueEntry entry) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM phase
                 WHERE entry_id = ?
                 ORDER BY priority DESC
                """,
                RowMappers.PHASE,
                entry.id());
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    public void startEntryProcessing(QueueEntry entry) {
        jdbc.update("""
                UPDATE entry
                   SET started=NOW(),
                       updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void updateEntryProcessing(QueueEntry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void completeEntryProcessing(QueueEntry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW(),
                       completed=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public List<ImmutableQueueEntry> findAllByBusinessId(String businessId, boolean full) {
        try {
            List<ImmutableQueueEntry> entries = jdbc.query("""
                    SELECT *
                      FROM entry
                     WHERE business_id = ?
                    """,
                RowMappers.QUEUE_ENTRY.apply(objectMapper),
                businessId);

            if (full) {
                return entries.stream()
                    .map(this::buildCompleteEntry)
                    .toList();
            } else {
                return entries;
            }
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    /**
     * Call this to complete {@link QueueEntry} object's fields if needed.
     *
     * @param entry Entry to complete.
     * @return Fully completed entry.
     */
    private ImmutableQueueEntry buildCompleteEntry(ImmutableQueueEntry entry) {
        return entry
            .withPhases(findPhases(entry.id()))
            .withValidation(findValidationInput(entry.id()))
            .withConversion(findConversionInput(entry.id()).orElse(null))
            .withErrors(errorHandlerRepository.findErrorsByEntryId(entry.id()));
    }

    private PGobject writeJson(JsonNode tree) {
        try {
            if (tree != null) {
                PGobject pgo = new PGobject();
                pgo.setType("jsonb");
                pgo.setValue(objectMapper.writeValueAsString(tree));
                return pgo;
            }
        } catch (SQLException | JsonProcessingException e) {
            logger.error("Failed Jdbc conversion from PGobject to JsonNode", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
    }
}
