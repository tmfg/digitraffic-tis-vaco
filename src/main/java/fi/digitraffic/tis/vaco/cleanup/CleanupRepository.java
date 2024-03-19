package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.db.RowMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Repository
public class CleanupRepository {

    private final JdbcTemplate jdbc;

    public CleanupRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public List<String> runCleanup(Duration olderThan, int keepAtLeast) {
        return jdbc.queryForList("""
              DELETE
                FROM entry e
               WHERE id IN (SELECT e.id AS id
                              FROM (SELECT e.id,
                                           e.created,
                                           ROW_NUMBER() OVER (PARTITION BY url ORDER BY created DESC) r
                                      FROM entry e) AS e
                             WHERE e.r > ?
                                OR e.created < NOW() - ?)
           RETURNING e.public_id;
            """,
            String.class,
            keepAtLeast, RowMappers.writeInterval(olderThan)
            );
    }
}
