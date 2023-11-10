package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public TaskRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Transactional
    public boolean createTasks(List<Task> tasks) {
        try {
            jdbc.batchUpdate("""
                INSERT INTO task (entry_id, name, priority)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, priority, created, started, updated, completed
                """,
                tasks,
                100,
                (ps, task) -> {
                    ps.setLong(1, task.entryId());
                    ps.setString(2, task.name());
                    ps.setLong(3, task.priority());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert tasks", dke);
            return false;
        }
    }

    public Task startTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET started = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.TASK,
            task.id());
    }

    public Task updateTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET updated = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.TASK,
            task.id());

    }

    public Task completeTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET updated = NOW(),
                        completed = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, created, started, updated, completed
            """,
            RowMappers.TASK,
            task.id());
    }

    /**
     * Finds all tasks for given entry, if any, ordered by priority.
     * <p>
     * The priority order is somewhat arbitrary and decided during insert.
     *
     * @param entryId Entry id reference for finding the tasks.
     * @return Ordered list of tasks or empty list if none found.
     */
    public List<Task> findTasks(long entryId) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM task
                 WHERE entry_id = ?
                 ORDER BY priority ASC
                """,
                RowMappers.TASK,
                entryId);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    public Optional<Task> findTask(Long entryId, String taskName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM task WHERE entry_id = ? AND name = ?",
                RowMappers.TASK,
                entryId, taskName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns a list of tasks which are at the time of querying ready to be executed as determined by not being started.
     * <p>
     * More specifically, the list of tasks is divided into priority groups based on flooring the priority to nearest
     * hundred. If within this group there are non-started tasks, those are returned. Additionally the last completed
     * task is checked to make sure that the next priority group isn't started too eagerly, as the following priority
     * group is awaiting for results from the previous priority group. This check is done by making sure that the
     * priority group of first non-completed task is within the same group as the one to execute.
     * <p>
     * Caller of this method should synchronously mark one or more of the tasks as started before allowing further
     * processing to occur.
     *
     * @param entry
     * @return
     * @see TaskService#createTasks(Entry)
     */
    public List<Task> findAvailableTasksToExecute(Entry entry) {
        return namedJdbc.query("""
              WITH first_available AS (SELECT FLOOR(priority / 100) AS priority_group
                                         FROM task
                                        WHERE entry_id = :entryId
                                          AND started IS NULL
                                        ORDER BY priority ASC
                                        LIMIT 1),
                   first_incomplete AS (SELECT FLOOR(priority / 100) AS priority_group
                                          FROM task
                                         WHERE entry_id = :entryId
                                           AND completed IS NULL
                                         ORDER BY priority ASC
                                         LIMIT 1)
                            SELECT first_available.priority_group,
                   first_incomplete.priority_group,
                   t.*
              FROM task t,
                   first_available,
                   first_incomplete
             WHERE FLOOR(t.priority / 100) = first_available.priority_group
               AND FLOOR(t.priority / 100) = first_incomplete.priority_group
               AND t.entry_id = :entryId
               AND t.started IS NULL
             ORDER BY priority ASC
            """,

            new MapSqlParameterSource()
                .addValue("entryId", entry.id()),
        RowMappers.TASK);
    }
}
