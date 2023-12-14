package fi.digitraffic.tis.vaco.findings;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Repository
public class FindingRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public FindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Finding create(Finding finding) {
        return jdbc.queryForObject("""
            INSERT INTO finding (entry_id, task_id, ruleset_id, message, raw)
                 VALUES (?, ?, ?, ?, ?)
              RETURNING id, public_id, entry_id, task_id, ruleset_id, message, raw
            """,
            RowMappers.FINDING,
            finding.entryId(), finding.taskId(), finding.rulesetId(), finding.message(), finding.raw());
    }

    public List<Finding> findErrorsByEntryId(Long entryId) {
        try {
            return jdbc.query(
                    """
                    SELECT id, public_id, entry_id, task_id, ruleset_id, source, message, severity, raw
                      FROM finding em
                     WHERE em.entry_id = ?
                    """,
                    RowMappers.FINDING,
                    entryId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    @Transactional
    public boolean createFindings(List<Finding> findings) {
        try {
            jdbc.batchUpdate("""
                        INSERT INTO finding (entry_id, task_id, ruleset_id, source, message, severity, raw)
                             VALUES (
                             (SELECT id FROM entry WHERE public_id = ?),
                             ?,
                             ?,
                             ?,
                             ?,
                             COALESCE((SELECT rso.severity
                                         FROM rule_severity_override rso
                                        WHERE rso.ruleset_id = ?
                                          AND rso.name = ?),
                                      ?),
                             ?)
                          RETURNING id, public_id, entry_id, task_id, ruleset_id, source, message, severity, raw
                    """,
                findings,
                100,
                (ps, finding) -> {
                    ps.setString(1, finding.entryId());
                    ps.setLong(2, finding.taskId());
                    ps.setLong(3, finding.rulesetId());
                    ps.setString(4, finding.source());
                    ps.setString(5, finding.message());
                    ps.setLong(6, finding.rulesetId());
                    ps.setString(7, finding.message());
                    ps.setString(8, finding.severity());
                    ps.setObject(9, finding.raw());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert tasks", dke);
            return false;
        }
    }

    public boolean hasErrors(Entry entry) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT COUNT(id) = 0
              FROM finding
             WHERE entry_id = ?
               AND severity = 'ERROR'
            """,
            Boolean.class,
            entry.id()));
    }

    public Map<String, Long> getSeverityCounts(Entry entry, Task task) {
        List<Map<String, Object>> mapList = jdbc.queryForList("""
                  SELECT severity,
                         COUNT(severity) AS count
                    FROM finding
                   WHERE entry_id = (SELECT id FROM entry WHERE entry.public_id = ?)
                     AND task_id = ?
                GROUP BY severity
                """,
            entry.publicId(),
            task.id());
        return Streams.collect(
            mapList,
            r -> (String) r.get("severity"),
            r -> (Long) r.get("count"));
    }
}
