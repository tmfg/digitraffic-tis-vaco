package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.process.model.Task;
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

    public FindingRecord create(Finding finding) {
        return jdbc.queryForObject("""
            INSERT INTO finding (task_id, ruleset_id, source, message, severity, raw)
                 VALUES (
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
              RETURNING id, public_id, task_id, ruleset_id, source, message, severity, raw
            """,
            RowMappers.FINDING_RECORD,
            finding.taskId(),
            finding.rulesetId(),
            finding.source(),
            finding.message(),
            finding.rulesetId(),
            finding.message(),
            finding.severity(),
            finding.raw());
    }

    public List<FindingRecord> findFindingsByTaskId(Long taskId) {
        try {
            return jdbc.query(
                """
                SELECT id, public_id, task_id, ruleset_id, source, message, severity, raw
                  FROM finding
                 WHERE task_id = ?
                """,
                RowMappers.FINDING_RECORD,
                taskId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    @Transactional
    public boolean createFindings(List<Finding> findings) {
        try {
            jdbc.batchUpdate("""
                        INSERT INTO finding (task_id, ruleset_id, source, message, severity, raw)
                             VALUES (
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
                          RETURNING id, public_id, task_id, ruleset_id, source, message, severity, raw
                    """,
                findings,
                100,
                (ps, finding) -> {
                    ps.setLong(1, finding.taskId());
                    ps.setLong(2, finding.rulesetId());
                    ps.setString(3, finding.source());
                    ps.setString(4, finding.message());
                    ps.setLong(5, finding.rulesetId());
                    ps.setString(6, finding.message());
                    ps.setString(7, finding.severity());
                    ps.setObject(8, finding.raw());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert tasks", dke);
            return false;
        }
    }

    public Map<String, Long> getSeverityCounts(Task task) {
        List<Map<String, Object>> mapList = jdbc.queryForList("""
                  SELECT severity,
                         COUNT(severity) AS count
                    FROM finding
                   WHERE task_id = ?
                GROUP BY severity
                """,
            task.id());
        return Streams.collect(
            mapList,
            r -> ((String) r.get("severity")).toUpperCase(),
            r -> (Long) r.get("count"));
    }

    public List<FindingRecord> findFindingsByName(Long taskId, String findingName) {
        try {
            return jdbc.query(
                """
                SELECT id, public_id, task_id, ruleset_id, source, message, severity, raw
                  FROM finding
                 WHERE task_id = ?
                   AND message = ?
                """,
                RowMappers.FINDING_RECORD,
                taskId,
                findingName);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }

    }
}
