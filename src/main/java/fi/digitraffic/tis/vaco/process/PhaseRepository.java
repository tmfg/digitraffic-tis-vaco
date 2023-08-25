package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PhaseRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public PhaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean createPhases(List<ImmutablePhase> phases) {
        try {
            int[][] result = jdbc.batchUpdate("""
                INSERT INTO phase (entry_id, name, priority)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, priority, started, updated, completed
                """,
                phases,
                100,
                (ps, phase) -> {
                    ps.setLong(1, phase.entryId());
                    ps.setString(2, phase.name());
                    ps.setLong(3, phase.priority());
                });
            // TODO: inspect result counts to determine everything was inserted
            return true;
        } catch (DuplicateKeyException dke) {
            logger.warn("Failed to batch insert phases", dke);
            return false;
        }
    }

    public ImmutablePhase startPhase(ImmutablePhase phase) {
        return jdbc.queryForObject("""
                 UPDATE phase
                    SET started = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, started, updated, completed
            """,
            RowMappers.PHASE,
            phase.id());
    }

    public ImmutablePhase updatePhase(ImmutablePhase phase) {
        return jdbc.queryForObject("""
                 UPDATE phase
                    SET updated = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, started, updated, completed
            """,
            RowMappers.PHASE,
            phase.id());

    }

    public ImmutablePhase completePhase(ImmutablePhase phase) {
        return jdbc.queryForObject("""
                 UPDATE phase
                    SET updated = NOW(),
                        completed = NOW()
                  WHERE id = ?
              RETURNING id, entry_id, name, priority, started, updated, completed
            """,
            RowMappers.PHASE,
            phase.id());
    }

    /**
     * Finds all phases for given entry, if any, ordered by priority.
     * <p>
     * The priority order is somewhat arbitrary and decided during insert.
     *
     * @param entry Entry reference for finding the phases.
     * @return Ordered list of phases or empty list if none found.
     */
    public List<ImmutablePhase> findPhases(long entryId) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM phase
                 WHERE entry_id = ?
                 ORDER BY priority DESC
                """,
                RowMappers.PHASE,
                entryId);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    /**
     * Returns simple count of known phases for given entry.
     * @param entry Entry to scan.
     * @return Number of phases for entry.
     */
    public long count(Entry entry) {
        return jdbc.queryForObject("SELECT COUNT(id) FROM phase WHERE entry_id = ?", Long.class, entry.id());
    }

    public ImmutablePhase findPhase(Long entryId, String phaseName) {
        return jdbc.queryForObject(
            "SELECT * FROM phase WHERE entry_id = ? AND name = ?",
            RowMappers.PHASE,
            entryId, phaseName);
    }
}
