package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.summary.model.ImmutableSummary;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SummaryRepository {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SummaryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    <T> void persistTaskSummaryItem(Long taskId, String itemName, RendererType rendererType, T data) {
        try {
            create(ImmutableSummary.of(taskId, itemName, rendererType, objectMapper.writeValueAsBytes(data)));
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to persist {}'s summary data {} generated for task {}", itemName, data, taskId, e);
        }
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
                 ORDER BY CASE
                    WHEN ts.name = 'agencies' THEN 1
                    WHEN ts.name = 'operators' THEN 1
                    WHEN ts.name = 'feedInfo' THEN 2
                    WHEN ts.name = 'lines' THEN 2
                    WHEN ts.name = 'files' THEN 3
                    WHEN ts.name = 'counts' THEN 4
                    WHEN ts.name = 'components' THEN 5
                    ELSE 6
                 END ASC
                """,
                RowMappers.SUMMARY_WITH_CONTENT.apply(objectMapper),
                entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
