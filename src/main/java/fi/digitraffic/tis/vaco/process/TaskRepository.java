package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean createTasks(List<ImmutableTask> phases) {
        try {
            int[][] result = jdbc.batchUpdate("""
                INSERT INTO task (entry_id, name, priority)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, priority, created, started, updated, completed
                """,
                phases,
                100,
                (ps, task) -> {
                    ps.setLong(1, task.entryId());
                    ps.setString(2, task.name());
                    ps.setLong(3, task.priority());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert phases", dke);
            return false;
        }
    }

    public ImmutableTask startTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET started = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.PHASE,
            task.id());
    }

    public ImmutableTask updateTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET updated = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.PHASE,
            task.id());

    }

    public ImmutableTask completeTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET updated = NOW(),
                        completed = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.PHASE,
            task.id());
    }

    /**
     * Finds all phases for given entry, if any, ordered by priority.
     * <p>
     * The priority order is somewhat arbitrary and decided during insert.
     *
     * @param entry Entry reference for finding the phases.
     * @return Ordered list of phases or empty list if none found.
     */
    public List<ImmutableTask> findTasks(long entryId) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM task
                 WHERE entry_id = ?
                 ORDER BY priority ASC
                """,
                RowMappers.PHASE,
                entryId);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    /**
     * Returns simple count of known phases for given entry.
     * @param entry Entry to scan.
     * @return Number of phases for entry.
     */
    public long count(Entry entry) {
        return jdbc.queryForObject("SELECT COUNT(id) FROM task WHERE entry_id = ?", Long.class, entry.id());
    }

    public ImmutableTask findTask(Long entryId, String phaseName) {
        return jdbc.queryForObject(
            "SELECT * FROM task WHERE entry_id = ? AND name = ?",
            RowMappers.PHASE,
            entryId, phaseName);
    }
}
