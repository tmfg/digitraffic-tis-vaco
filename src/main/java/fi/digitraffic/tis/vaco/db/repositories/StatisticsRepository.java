package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import fi.digitraffic.tis.vaco.db.model.StatisticsRecord;

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

    public List<StatisticsRecord> listEntryStatusStatistics() {
        try {
            return jdbc.query("""
               SELECT name, subserie, date_trunc('day', record_created_at)::date AS record_created_at, unit, count, series
               FROM status_statistics
               WHERE series = 'entry-statuses'
               AND record_created_at >= now() - interval '30 days';
                """,
                RowMappers.STATISTICS_RECORD);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    public List<StatisticsRecord> listTaskStatusStatistics(){

        try {
            return jdbc.query("""
               SELECT name, subserie, date_trunc('day', record_created_at)::date AS record_created_at, unit, count, series
               FROM status_statistics
               WHERE series = 'task-statuses'
               AND record_created_at >= now() - interval '30 days';
                """,
                RowMappers.STATISTICS_RECORD);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }

    }

    public void refreshView() {
        String refreshSql = "REFRESH MATERIALIZED VIEW status_statistics";
        jdbcTemplate.execute(refreshSql);
    }
}
