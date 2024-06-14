package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.db.RowMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Repository
public class CleanupRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public CleanupRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /**
     * Removes entries which are older than specified, keeping at least specified amount of entries for each url.
     *
     * @param olderThan           Age limit for entries
     * @param keepAtLeast         How many entries should be kept at minimum
     * @param removeAtMostInTotal How many entries should be deleted by a single run at most
     * @return Public id:s of entries removed by history cleanup.
     */
    public List<String> cleanupHistory(Duration olderThan, int keepAtLeast, int removeAtMostInTotal) {
        try {
            return jdbc.queryForList(
                """
                   DELETE
                     FROM entry e
                    WHERE id IN (SELECT e.id AS id
                                   FROM (SELECT e.id,
                                                e.created,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created DESC) newest_to_oldest
                                           FROM entry e) AS e
                                  WHERE e.newest_to_oldest > ?
                                     OR e.created < NOW() - ?
                                  LIMIT ?)
                RETURNING e.public_id;
                """,
                String.class,
                keepAtLeast, RowMappers.writeInterval(olderThan), removeAtMostInTotal
            );
        } catch (DataAccessException dae) {
            logger.warn("Failed to cleanup entry history, returning empty list", dae);
            return List.of();
        }
    }

    /**
     * Compression removes in-between entries which bear no significant value.
     * <ul>
     *     <li>only keep first and latest {@link fi.digitraffic.tis.vaco.entries.model.Status#CANCELLED} entry for each feed</li>
     * </ul>
     * @return Public id:s of entries removed by compression.
     */
    public List<String> compressHistory() {
        try {
            return jdbc.queryForList(
                """
                   DELETE
                     FROM entry e
                    WHERE id IN (SELECT e.id AS id
                                   FROM (SELECT e.id,
                                                e.created,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created DESC) newest_to_oldest,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created ASC) oldest_to_newest
                                           FROM entry e
                                          WHERE e.status = 'cancelled') AS e
                                  WHERE e.newest_to_oldest != 1
                                    AND e.oldest_to_newest != 1)
                RETURNING e.public_id;
                """,
                String.class
            );
        } catch (DataAccessException dae) {
            logger.warn("Failed to cleanup entry history, returning empty list", dae);
            return List.of();
        }
    }
}
