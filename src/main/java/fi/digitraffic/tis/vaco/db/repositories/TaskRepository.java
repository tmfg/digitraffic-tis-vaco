package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
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
import java.util.Objects;
import java.util.Optional;

@Repository
public class TaskRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public TaskRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Transactional
    public boolean createTasks(EntryRecord entry, List<Task> tasks) {
        try {
            int[][] result = jdbc.batchUpdate(
                """
                INSERT INTO task (entry_id, name, priority)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, priority, created, started, updated, completed, status
                """,
                tasks,
                100,
                (ps, task) -> {
                    ps.setLong(1, entry.id());
                    ps.setString(2, task.name());
                    ps.setLong(3, task.priority());
                });
            for (int[] batch : result) {
                for (int insert : batch) {
                    if (insert != 0) {
                        logger.warn("Not all tasks were successfully created for entry {} ({})", entry.publicId(), result);
                        return false;
                    }
                }
            }
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert tasks", dke);
            return false;
        }
    }

    public Task startTask(Task task) {
        Task started = jdbc.queryForObject("""
                     UPDATE task
                        SET started = NOW()
                      WHERE id = ?
                  RETURNING id, entry_id, public_id, name, priority, created, started, updated, completed, status
                """,
            RowMappers.TASK,
            task.id());
        return markStatus(started, Status.PROCESSING);
    }

    public Task updateTask(Task task) {
        return jdbc.queryForObject("""
                 UPDATE task
                    SET updated = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, public_id, name, priority, created, started, updated, completed, status
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
              RETURNING id, entry_id, public_id, name, priority, created, started, updated, completed, status
            """,
            RowMappers.TASK,
            task.id());
    }

    public List<Task> findTasks(String publicId) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM task
                 WHERE entry_id = (SELECT id FROM entry WHERE public_id = ?)
                 ORDER BY priority ASC
                """,
                RowMappers.TASK,
                publicId);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
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

    public Optional<Task> findTask(String publicId, String taskName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT *
                  FROM task
                 WHERE entry_id = (SELECT id FROM entry WHERE public_id = ?) AND name = ?
                """,
                RowMappers.TASK,
                publicId, taskName));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
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

    public Optional<Task> findTask(String taskPublicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM task WHERE public_id = ?",
                RowMappers.TASK,
                taskPublicId));
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
     * @see TaskService#createTasks(EntryRecord)
     */
    public List<Task> findAvailableTasksToExecute(Entry entry) {
        return namedJdbc.query("""
              WITH entry AS (SELECT id
                               FROM entry
                               WHERE public_id = :publicId),
                   first_available AS (SELECT FLOOR(priority / 100) AS priority_group
                                         FROM task, entry
                                        WHERE entry_id = entry.id
                                          AND started IS NULL
                                        ORDER BY priority ASC
                                        LIMIT 1),
                   first_incomplete AS (SELECT FLOOR(priority / 100) AS priority_group
                                          FROM task, entry
                                         WHERE entry_id = entry.id
                                           AND completed IS NULL
                                         ORDER BY priority ASC
                                         LIMIT 1)
                            SELECT first_available.priority_group,
                   first_incomplete.priority_group,
                   t.*
              FROM task t,
                   entry e,
                   first_available,
                   first_incomplete
             WHERE FLOOR(t.priority / 100) = first_available.priority_group
               AND FLOOR(t.priority / 100) = first_incomplete.priority_group
               AND t.entry_id = e.id
               AND t.started IS NULL
             ORDER BY priority ASC
            """,

            new MapSqlParameterSource()
                .addValue("publicId", entry.publicId()),
        RowMappers.TASK);
    }

    public boolean areAllTasksCompleted(Entry entry) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT NOT EXISTS(SELECT 1
                            FROM task
                           WHERE entry_id = (SELECT id FROM entry WHERE public_id = ?)
                             AND completed IS NULL);
            """,
            Boolean.class,
            entry.publicId()));
    }

    public Task markStatus(Task task, Status status) {
        return jdbc.queryForObject("""
                     UPDATE task
                        SET status = (?)::status
                      WHERE id = ?
                  RETURNING id, entry_id, public_id, name, priority, created, started, updated, completed, status
                """,
            RowMappers.TASK,
            status.fieldName(),
            task.id());
    }

    public List<ValidationInput> findValidationInputs(EntryRecord entry) {
        return jdbc.query("SELECT * FROM validation_input qvi WHERE qvi.entry_id = ?",
            RowMappers.VALIDATION_INPUT.apply(objectMapper),
            entry.id());
    }

    public List<ConversionInput> findConversionInputs(EntryRecord entry) {
        return jdbc.query("SELECT * FROM conversion_input qci WHERE qci.entry_id = ?",
            RowMappers.CONVERSION_INPUT.apply(objectMapper),
            entry.id());
    }

}
