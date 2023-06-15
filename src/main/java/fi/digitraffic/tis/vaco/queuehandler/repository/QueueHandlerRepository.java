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
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueHandlerRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ErrorHandlerRepository errorHandlerRepository;

    public QueueHandlerRepository(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  ErrorHandlerRepository errorHandlerRepository) {
        this.jdbcTemplate = jdbcTemplate;
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
        return jdbcTemplate.queryForObject("""
                INSERT INTO queue_entry(business_id, format, url, etag, metadata)
                     VALUES (?, ?, ?, ?, ?)
                  RETURNING id, public_id, business_id, format, url, etag, metadata, started, updated, completed
                """,
            RowMappers.QUEUE_ENTRY.apply(objectMapper),
            entry.businessId(), entry.format(), entry.url(), entry.etag(), writeJson(entry.metadata()));
    }

    private List<ImmutablePhase> createPhases(Long entryId, List<Phase> phases) {
        if (phases == null) {
            return List.of();
        }
        return phases.stream()
                .map(phase -> jdbcTemplate.queryForObject(
                        "INSERT INTO queue_phase(entry_id, name) VALUES (?, ?) RETURNING id, name, started",
                        RowMappers.PHASE,
                        entryId))
                .toList();
    }

    private ValidationInput createValidationInput(Long entryId, ValidationInput validation) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO queue_validation_input (entry_id) VALUES (?) RETURNING id, entry_id",
                (rs, rowNum) -> ImmutableValidationInput.builder().build(),
                entryId);
    }

    private ConversionInput createConversionInput(Long entryId, ConversionInput conversion) {
        return conversion != null
            ? jdbcTemplate.queryForObject(
                "INSERT INTO queue_conversion_input (entry_id, target_format) VALUES (?, ?) RETURNING id, entry_id, target_format",
                    (rs, rowNum) -> ImmutableConversionInput.builder().build(),
                    entryId, conversion.targetFormat())
            : null;
    }

    @Transactional
    public Optional<ImmutableQueueEntry> findByPublicId(String publicId) {
        return findEntry(publicId).map(entry ->
                entry.withPhases(findPhases(entry.id()))
                        .withValidation(findValidationInput(entry.id()))
                        .withConversion(findConversionInput(entry.id()).orElse(null))
                        .withErrors(errorHandlerRepository.findErrorsByEntryId(entry.id())));
    }

    private Optional<ImmutableQueueEntry> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                        SELECT id, public_id, business_id, format, url, etag, metadata, started, updated, completed
                          FROM queue_entry qe
                         WHERE qe.public_id = ?
                        """,
                        RowMappers.QUEUE_ENTRY.apply(objectMapper),
                        publicId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    private List<ImmutablePhase> findPhases(Long entryId) {
        return jdbcTemplate.query("SELECT * FROM queue_phase qp WHERE qp.entry_id = ?",
                RowMappers.PHASE,
            entryId);
    }

    private ValidationInput findValidationInput(Long entryId) {
        return jdbcTemplate.queryForObject("SELECT * FROM queue_validation_input qvi WHERE qvi.entry_id = ?",
            (rs, row) -> null,
            entryId);
    }

    private Optional<ConversionInput> findConversionInput(Long entryId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT * FROM queue_conversion_input qci WHERE qci.entry_id = ?",
                (rs, row) -> null,
                entryId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public ImmutablePhase startPhase(ImmutablePhase phase) {
        try {
            return jdbcTemplate.queryForObject("""
                INSERT INTO queue_phase (entry_id, name, updated)
                     VALUES (?, ?, NOW())
                  RETURNING id, entry_id, name, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.entryId(), phase.name());
        } catch (DuplicateKeyException dke) {
            throw new ValidationProcessException("Failed to start phase " + phase + ", did you try to START the same phase twice?", dke);
        }
    }

    public ImmutablePhase updatePhase(ImmutablePhase phase) {
        return jdbcTemplate.queryForObject("""
                     UPDATE queue_phase
                        SET updated = NOW()
                      WHERE id = ?
                  RETURNING id, entry_id, name, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.id());

    }

    public ImmutablePhase completePhase(ImmutablePhase phase) {
        return jdbcTemplate.queryForObject("""
                     UPDATE queue_phase
                        SET updated = NOW(),
                            completed = NOW()
                      WHERE id = ?
                  RETURNING id, entry_id, name, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.id());
    }

    public void startEntryProcessing(QueueEntry entry) {
        jdbcTemplate.update("""
                UPDATE queue_entry
                   SET started=NOW(),
                       updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void updateEntryProcessing(QueueEntry entry) {
        jdbcTemplate.update("""
                UPDATE queue_entry
                   SET updated=NOW()
                 WHERE id = ?
                """,
                entry.id());
    }

    public void completeEntryProcessing(QueueEntry entry) {
        jdbcTemplate.update("""
                UPDATE queue_entry
                   SET updated=NOW(),
                       completed=NOW()
                 WHERE id = ?
                """,
                entry.id());
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
            LOGGER.error("Failed Jdbc conversion from PGobject to JsonNode", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
    }
}
