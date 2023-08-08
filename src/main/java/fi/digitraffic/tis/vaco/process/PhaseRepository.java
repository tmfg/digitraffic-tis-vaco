package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.validation.ValidationProcessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PhaseRepository {

    private final JdbcTemplate jdbc;

    public PhaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImmutablePhase startPhase(ImmutablePhase phase) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO phase (entry_id, name, priority, updated)
                     VALUES (?, ?, ?, NOW())
                  RETURNING id, entry_id, name, priority, started, updated, completed
                """,
                RowMappers.PHASE,
                phase.entryId(), phase.name(), phase.priority());
        } catch (DuplicateKeyException dke) {
            throw new ValidationProcessException("Failed to start phase " + phase + ", did you try to START the same phase twice?", dke);
        }
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
    public List<ImmutablePhase> findPhases(Entry entry) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM phase
                 WHERE entry_id = ?
                 ORDER BY priority DESC
                """,
                RowMappers.PHASE,
                entry.id());
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
}
