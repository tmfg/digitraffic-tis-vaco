package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class QueueHandlerRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueHandlerRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QueueHandlerRepository(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
                INSERT INTO queue_entry(format, url, etag, metadata)
                     VALUES (?,?,?,?)
                  RETURNING id, public_id, format, url, etag, metadata
                """,
            (rs, rowNum) -> ImmutableQueueEntry.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getString("public_id"))
                .format(rs.getString("format"))
                .url(rs.getString("url"))
                .etag(rs.getString("etag"))
                .metadata(readJson(rs, "metadata"))
                .build(),
            entry.format(), entry.url(), entry.etag(), writeJson(entry.metadata()));
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
        ValidationInput result = jdbcTemplate.queryForObject(
                "INSERT INTO queue_validation_input (entry_id) VALUES (?) RETURNING id, entry_id",
                (rs, rowNum) -> ImmutableValidationInput.builder().build(),
                entryId);
        return result;
    }

    private ConversionInput createConversionInput(Long entryId, ConversionInput conversion) {
        ConversionInput result = jdbcTemplate.queryForObject(
                "INSERT INTO queue_Conversion_input (entry_id) VALUES (?) RETURNING id, entry_id",
                (rs, rowNum) -> ImmutableConversionInput.builder().build(),
                entryId);
        return result;
    }

    @Transactional
    public Optional<ImmutableQueueEntry> findByPublicId(String publicId) {
        return findEntry(publicId).map(entry ->
            entry.withPhases(findPhases(entry.id()))
                .withValidation(findValidationInput(entry.id()))
                .withConversion((findConversionInput(entry.id()))));
    }

    private Optional<ImmutableQueueEntry> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, public_id, format, url, etag, metadata FROM queue_entry qe WHERE qe.public_id = ?",
                (rs, row) -> ImmutableQueueEntry.builder()
                    .id(rs.getLong("id"))
                    .publicId(rs.getString("public_id"))
                    .format(rs.getString("format"))
                    .url(rs.getString("url"))
                    .etag(rs.getString("etag"))
                    .metadata(readJson(rs, "metadata"))
                    .build(),
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

    private ConversionInput findConversionInput(Long entryId) {
        return jdbcTemplate.queryForObject("SELECT * FROM queue_conversion_input qci WHERE qci.entry_id = ?",
            (rs, row) -> null,
            entryId);
    }


    private JsonNode readJson(ResultSet rs, String metadata) {
        try {
            PGobject source = (PGobject) rs.getObject(metadata);
            if (source != null) {
                return objectMapper.readTree(source.getValue());
            }
        } catch (SQLException | JsonProcessingException e) {
            LOGGER.error("Failed Jdbc conversion from PGobject to JsonNode", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
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

    public ImmutablePhase startPhase(ImmutablePhase phase) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO queue_phase (entry_id, name, updated)
                     VALUES (?, ?, NOW())
                  RETURNING id, entry_id, name, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.entryId(), phase.name());
    }

    public ImmutablePhase completePhase(ImmutablePhase phase) {
        return jdbcTemplate.queryForObject("""
                     UPDATE queue_phase
                        SET updated = NOW(),
                            completed = NOW()
                      WHERE entry_id = ? AND name = ?
                  RETURNING id, entry_id, name, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.entryId(), phase.name());
    }
}
