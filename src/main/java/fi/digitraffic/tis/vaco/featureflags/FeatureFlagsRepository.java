package fi.digitraffic.tis.vaco.featureflags;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class FeatureFlagsRepository {

    private final JdbcTemplate jdbc;

    public FeatureFlagsRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public List<FeatureFlag> listFeatureFlags() {
        return jdbc.query("""
            SELECT ff.*
              FROM feature_flag ff
            """,
            RowMappers.FEATURE_FLAG);
    }

    public Optional<FeatureFlag> findFeatureFlag(String name) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT ff.*
                  FROM feature_flag ff
                 WHERE name = ?
                """,
                RowMappers.FEATURE_FLAG,
                name));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public FeatureFlag setFeatureFlag(FeatureFlag featureFlag, boolean enabled, String modifierOid) {
        return jdbc.queryForObject("""
               UPDATE feature_flag
                  SET name = ?,
                      enabled = ?,
                      modified_by = ?
               WHERE id = ?
            RETURNING *
            """,
            RowMappers.FEATURE_FLAG,
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
