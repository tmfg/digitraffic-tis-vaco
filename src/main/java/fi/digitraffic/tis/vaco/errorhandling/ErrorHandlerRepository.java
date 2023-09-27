package fi.digitraffic.tis.vaco.errorhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ErrorHandlerRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ErrorHandlerRepository(ObjectMapper objectMapper,
                                  JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public ImmutableError create(Error error) {
        return jdbc.queryForObject("""
            INSERT INTO error (entry_id, task_id, ruleset_id, message, raw)
                 VALUES (?, ?, ?, ?, ?)
              RETURNING id, public_id, entry_id, task_id, ruleset_id, message, raw
            """,
            RowMappers.ERROR,
            error.entryId(), error.taskId(), error.rulesetId(), error.message(), error.raw());
    }

    public List<ImmutableError> findErrorsByEntryId(Long entryId) {
        try {
            return jdbc.query(
                    """
                    SELECT id, public_id, entry_id, task_id, ruleset_id, source, message, raw
                      FROM error em
                     WHERE em.entry_id = ?
                    """,
                    RowMappers.ERROR,
                    entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }

    }

    @Transactional
    public boolean createErrors(List<Error> errors) {
        try {
            int[][] result = jdbc.batchUpdate("""
                INSERT INTO error (entry_id, task_id, ruleset_id, source, message, raw)
                     VALUES ((SELECT id FROM entry WHERE public_id = ?), ?, ?, ?, ?, ?)
                  RETURNING id, public_id, entry_id, task_id, ruleset_id, source, message, raw
                """,
                errors,
                100,
                (ps, error) -> {
                    ps.setString(1, error.entryId());
                    ps.setLong(2, error.taskId());
                    ps.setLong(3, error.rulesetId());
                    ps.setString(4, error.source());
                    ps.setString(5, error.message());
                    ps.setObject(6, error.raw());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert tasks", dke);
            return false;
        }
    }
}
