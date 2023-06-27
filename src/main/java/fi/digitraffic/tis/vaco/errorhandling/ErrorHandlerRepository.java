package fi.digitraffic.tis.vaco.errorhandling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.InvalidMappingException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ErrorHandlerRepository {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ErrorHandlerRepository(ObjectMapper objectMapper,
                                  JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public ImmutableError create(Error error) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO error (entry_id, phase_id, ruleset_id, message, raw)
                     VALUES (?, ?, ?, ?, ?)
                  RETURNING id, public_id, entry_id, phase_id, ruleset_id, message, raw
                """,
                RowMappers.ERROR.apply(objectMapper),
                error.entryId(), error.phaseId(), error.rulesetId(), error.message(), objectMapper.writeValueAsString(error.raw()).getBytes());
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Failed to convert JsonNode to bytes[]", e);
        }
    }

    public List<ImmutableError> findErrorsByEntryId(Long entryId) {
        try {
            return jdbc.query(
                    """
                    SELECT id, public_id, entry_id, phase_id, ruleset_id, message, raw
                      FROM error em
                     WHERE em.entry_id = ?
                    """,
                    RowMappers.ERROR.apply(objectMapper),
                    entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }

    }

}
