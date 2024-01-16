package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SummaryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SummaryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Summary create(Summary summary) {
        return jdbc.queryForObject("""
            INSERT INTO summary (task_id, name, renderer_type, raw)
                 VALUES (?, ?, ?::summary_renderer_type, ?)
              RETURNING id, task_id, name, renderer_type, raw
            """,
            RowMappers.SUMMARY,
            summary.taskId(), summary.name(), summary.rendererType().fieldName(), summary.raw());
    }

    public List<Summary> findTaskSummaryByTaskId(Long taskId) {
        try {
            return jdbc.query(
                """
                SELECT ts.id, ts.task_id, ts.name, ts.renderer_type, ts.raw
                  FROM summary ts
                 WHERE ts.task_id = ?
                """,
                RowMappers.SUMMARY,
                taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<Summary> findTaskSummaryByEntryId(Long entryId) {
        try {
            return jdbc.query(
                """
                SELECT ts.id, ts.task_id, ts.name, ts.renderer_type, ts.raw
                  FROM summary ts
                  JOIN task t ON ts.task_id = t.id
                 WHERE t.entry_id = ?
                """,
                RowMappers.SUMMARY_WITH_CONTENT.apply(objectMapper),
                entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
