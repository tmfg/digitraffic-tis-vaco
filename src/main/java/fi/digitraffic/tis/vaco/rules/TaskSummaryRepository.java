package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.rules.model.TaskSummaryItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskSummaryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TaskSummaryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public TaskSummaryItem create(TaskSummaryItem taskSummaryItem) {
        return jdbc.queryForObject("""
            INSERT INTO summary (task_id, name, raw)
                 VALUES (?, ?, ?)
              RETURNING id, task_id, name, raw
            """,
            RowMappers.SUMMARY,
            taskSummaryItem.taskId(), taskSummaryItem.name(), taskSummaryItem.raw());
    }

    public List<TaskSummaryItem> findTaskSummaryByTaskId(Long taskId) {
        try {
            return jdbc.query(
                """
                SELECT ts.id, ts.task_id, ts.name, ts.raw
                  FROM summary ts
                 WHERE ts.task_id = ?
                """,
                RowMappers.SUMMARY,
                taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<TaskSummaryItem> findTaskSummaryByEntryId(Long entryId) {
        try {
            return jdbc.query(
                """
                SELECT ts.id, ts.task_id, ts.name, ts.raw
                  FROM summary ts
                  JOIN task t ON ts.task_id = t.id
                 WHERE t.entry_id = ?
                """,
                RowMappers.SUMMARY,
                entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
