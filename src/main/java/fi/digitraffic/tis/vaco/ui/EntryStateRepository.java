package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.Notice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class EntryStateRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;


    public EntryStateRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<Notice> findValidationRuleNotices(Long taskId) {
        try {
            return jdbc.query("""
                      SELECT message AS code,
                             severity,
                             COUNT(*) AS total
                        FROM finding
                       WHERE task_id = ?
                    GROUP BY message, severity
                    ORDER BY CASE
                                WHEN severity = 'ERROR' THEN 1
                                WHEN severity = 'WARNING' THEN 2
                                WHEN severity = 'INFO' THEN 3
                                ELSE 4
                             END ASC,
                             message ASC""",
                RowMappers.UI_NOTICES.apply(objectMapper),
                taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<ItemCounter> findValidationRuleCounters(Long taskId) {
        try {
            return jdbc.query("""
                    ( SELECT 'ALL' AS name,
                             COUNT(*) AS total
                        FROM finding
                       WHERE task_id = ?)
                    UNION ALL
                    ( SELECT severity AS name,
                             COUNT(*) AS total
                        FROM finding
                       WHERE task_id = ?
                    GROUP BY severity)
                    """,
                RowMappers.UI_NOTICE_COUNTERS.apply(objectMapper),
                taskId, taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<Finding> findNoticeInstances(Long taskId, String code) {
        try {
            return jdbc.query(
                """
                SELECT id, public_id, entry_id, task_id, ruleset_id, source, message, severity, raw
                  FROM finding em
                 WHERE em.task_id = ? and em.message = ?
                 LIMIT 20
                """,
                RowMappers.FINDING,
                taskId, code);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
