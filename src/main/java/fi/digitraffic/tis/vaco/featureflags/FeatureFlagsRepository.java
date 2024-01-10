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
            SELECT r.*
              FROM (SELECT ff.*,
                           ROW_NUMBER() OVER (PARTITION BY name ORDER BY modified DESC) i
                      FROM feature_flag ff) AS r
             WHERE r.i = 1
            """,
            RowMappers.FEATURE_FLAG);
    }

    public Optional<FeatureFlag> findFeatureFlag(String name) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT r.*
                  FROM (SELECT ff.*,
                               ROW_NUMBER() OVER (PARTITION BY name ORDER BY modified DESC) i
                          FROM feature_flag ff
                         WHERE name = ?) AS r
                 WHERE r.i = 1;
                """,
                RowMappers.FEATURE_FLAG,
                name));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public FeatureFlag setFeatureFlag(FeatureFlag featureFlag, boolean enabled, String modifierOid) {
        return jdbc.queryForObject("""
               INSERT INTO feature_flag (name, enabled, modified_by)
               VALUES (?, ?, ?)
            RETURNING *
            """,
            RowMappers.FEATURE_FLAG,
            featureFlag.name(), enabled, modifierOid);
    }

    public boolean isFeatureFlagEnabled(String name) {
        return Boolean.FALSE.equals(jdbc.queryForObject("""
            SELECT r.enabled
              FROM (SELECT ff.enabled,
                           ROW_NUMBER() OVER (PARTITION BY name ORDER BY modified DESC) i
                      FROM feature_flag ff
                     WHERE name = ?) AS r
             WHERE r.i = 1;
            """,
            Boolean.class,
            name));
    }
}
