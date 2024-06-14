package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class BadgeRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public BadgeRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Optional<Status> findEntryStatus(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT status
                  FROM entry
                 WHERE public_id = ?
                """,
                RowMappers.STATUS,
                publicId));
        } catch (DataAccessException dae) {
            logger.warn("Failed to fetch status of entry {}", publicId);
            return Optional.empty();
        }
    }

    public Optional<Status> findTaskStatus(EntryRecord entry, String taskName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT t.status
                      FROM task t
                     WHERE t.entry_id = (SELECT id FROM entry e WHERE e.id = ?)
                       AND t.name = ?
                    """,
                    RowMappers.STATUS,
                    entry.id(),
                    taskName));
        } catch (DataAccessException dae) {
            logger.warn("Failed to fetch status of entry {}'s task {}", entry.id(), taskName);
            return Optional.empty();
        }
    }
}
