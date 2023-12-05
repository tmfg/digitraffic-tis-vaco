package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ImmutableNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EntryStateRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;


    public EntryStateRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<ImmutableNotice> findValidationRuleNotices(Long taskId) {
        try {
            return jdbc.query("select message as code, severity, count(*) as total " +
                    "from vaco.vaco.error where task_id = ? " +
                    "group by message, severity " +
                    "order by case when severity = 'ERROR' " +
                    "   then 1 when severity = 'WARNING' " +
                    "   then 2 when severity = 'INFO' " +
                    "   then 3 else 4 end asc, " +
                    "   message asc",
                RowMappers.UI_NOTICES.apply(objectMapper),
                taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<ImmutableItemCounter> findValidationRuleCounters(Long taskId) {
        try {
            return jdbc.query(
                "(select 'ALL' as name, count(*) as total from vaco.vaco.error " +
                    "where task_id = ?) " +
                    "union all " +
                    "(select severity as name, count(*) as total " +
                    "from vaco.vaco.error where task_id = ? " +
                    "group by severity)",
                RowMappers.UI_NOTICE_COUNTERS.apply(objectMapper),
                taskId, taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<ImmutableError> findNoticeInstances(Long taskId, String code) {
        try {
            return jdbc.query(
                """
                SELECT id, public_id, entry_id, task_id, ruleset_id, source, message, severity, raw
                  FROM error em
                 WHERE em.task_id = ? and em.message = ?
                 LIMIT 20
                """,
                RowMappers.ERROR,
                taskId, code);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }
}
