package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.FeatureFlagRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class FeatureFlagRepository {

    private final JdbcTemplate jdbc;

    public FeatureFlagRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public List<FeatureFlagRecord> listFeatureFlags() {
        return jdbc.query("""
            SELECT ff.*
              FROM feature_flag ff
            """,
            RowMappers.FEATURE_FLAG_RECORD);
    }

    public Optional<FeatureFlagRecord> findFeatureFlag(String name) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT ff.*
                  FROM feature_flag ff
                 WHERE name = ?
                """,
                RowMappers.FEATURE_FLAG_RECORD,
                name));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public FeatureFlagRecord setFeatureFlag(FeatureFlagRecord featureFlag, boolean enabled, String modifierOid) {
        return jdbc.queryForObject("""
               UPDATE feature_flag
                  SET name = ?,
                      enabled = ?,
                      modified = NOW(),
                      modified_by = ?
               WHERE id = ?
            RETURNING *
            """,
            RowMappers.FEATURE_FLAG_RECORD,
            featureFlag.name(),
            enabled,
            modifierOid,
            featureFlag.id());
    }

    public boolean isFeatureFlagEnabled(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT ff.enabled
              FROM feature_flag ff
             WHERE name = ?
            """,
            Boolean.class,
            name));
    }
}
