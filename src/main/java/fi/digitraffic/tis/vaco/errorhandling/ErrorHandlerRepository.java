package fi.digitraffic.tis.vaco.errorhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ErrorHandlerRepository {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public ErrorHandlerRepository(ObjectMapper objectMapper,
                                  JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImmutableError create(Error error) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO error_message (entry_id, phase_id, ruleset_id, message, raw)
                 VALUES (?, ?, ?, ?, ?)
              RETURNING id, public_id, entry_id, phase_id, ruleset_id, message, raw
            """,
            RowMappers.ERROR.apply(objectMapper),
            error.entryId(), error.phaseId(), error.rulesetId(), error.message(), error.raw());
    }

    public List<ImmutableError> findErrorsByEntryId(Long entryId) {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT id, public_id, entry_id, phase_id, ruleset_id, message, raw
                      FROM error_message em
                     WHERE em.entry_id = ?
                    """,
                    RowMappers.ERROR.apply(objectMapper),
                    entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }

    }

}
