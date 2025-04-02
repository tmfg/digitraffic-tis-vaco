package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.StatusStatisticsRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StatisticsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public StatisticsRepository(JdbcTemplate jdbcTemplate, JdbcTemplate jdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
    }

    public List<StatusStatisticsRecord> listStatusStatistics () {
        try {
            return jdbc.query("""
               SELECT status, count, date_trunc('day', record_created_at)::date AS record_created_at, unit
               FROM status_statistics
               ORDER BY status, record_created_at ASC
                """,
                RowMappers.STATUS_STATISTICS_RECORD());
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    public void refreshView() {
        String refreshSql = "REFRESH MATERIALIZED VIEW status_statistics";
        jdbcTemplate.execute(refreshSql);
    }
}
