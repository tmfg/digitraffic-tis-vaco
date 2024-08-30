package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.SummaryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Deprecated(since = "2024-08-29")
@Repository
public class SummaryRepository {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SummaryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public SummaryRecord create(Summary summary) {
        return jdbc.queryForObject("""
            INSERT INTO summary (task_id, name, renderer_type, raw)
                 VALUES (?, ?, ?::summary_renderer_type, ?)
              RETURNING id, task_id, name, renderer_type, raw, created
            """,
            RowMappers.SUMMARY_RECORD,
            summary.taskId(),
            summary.name(),
            summary.rendererType().fieldName(),
            summary.raw());
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

    // TODO: check if this can be migrated to PersistentEntry
    public List<Summary> findTaskSummaryByEntry(Entry entry) {
        try {
            return jdbc.query(
                """
                SELECT ts.id, ts.task_id, ts.name, ts.renderer_type, ts.raw
                  FROM summary ts
                  JOIN task t ON ts.task_id = t.id
                 WHERE t.entry_id = (SELECT id FROM entry WHERE public_id = ?)
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
                entry.publicId());
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
