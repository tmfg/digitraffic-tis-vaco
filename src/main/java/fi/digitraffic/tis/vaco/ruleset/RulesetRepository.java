package fi.digitraffic.tis.vaco.ruleset;

import com.github.benmanes.caffeine.cache.Cache;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RulesetRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Cache<String, Ruleset> rulesetNameCache;

    public RulesetRepository(JdbcTemplate jdbcTemplate,
                             NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             @Qualifier("rulesetNameCache") Cache<String, Ruleset> rulesetNameCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.rulesetNameCache = warmup(rulesetNameCache);
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<Ruleset> findRulesets(String businessId, Type type) {
        List<ImmutableRuleset> rulesets = namedParameterJdbcTemplate.query("""
                WITH current_id AS (
                    SELECT id
                      FROM organization
                     WHERE business_id = :businessId
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM cooperation, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT r.*
                  FROM ruleset r, current_id
                 WHERE r.owner_id = current_id.id AND r.type = :type
                UNION
                SELECT DISTINCT r.*
                  FROM ruleset r, parents
                 WHERE r.owner_id IN (parents.id)
                   AND r.category = 'generic' AND r.type = :type
                """,
            new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("type", type.fieldName()),
            RowMappers.RULESET);
        logger.info("Found {} rulesets for {}: {}", rulesets.size(), businessId, rulesets.stream().map(Ruleset::identifyingName).toList());
        return Set.copyOf(rulesets);
    }

    public Set<Ruleset> findRulesets(String businessId, Set<String> rulesetNames, Type type) {
        if (rulesetNames.isEmpty()) {
            return findRulesets(businessId, type);
        }
        List<ImmutableRuleset> rulesets = namedParameterJdbcTemplate.query("""
                WITH current_id AS (
                    SELECT id
                      FROM organization
                     WHERE business_id = :businessId
                ),
                specific_rulesets AS (
                    SELECT id
                      FROM ruleset
                     WHERE identifying_name IN (:rulesetNames) AND "type" = :type
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM cooperation, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT r.*
                  FROM ruleset r, current_id, specific_rulesets
                 WHERE r.owner_id = current_id.id
                   AND r.id IN (specific_rulesets.id)
                   AND r.type = :type
                UNION
                SELECT DISTINCT r.*
                  FROM ruleset r, parents, specific_rulesets
                 WHERE r.owner_id IN (parents.id)
                   AND r.category = 'generic'
                """,
            new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("rulesetNames", rulesetNames)
                .addValue("type", type.fieldName()),
            RowMappers.RULESET);
        logger.info("Found {} rulesets of type {} for {}: resolved {}, requested {}", rulesets.size(), type,
            businessId, rulesets.stream().map(Ruleset::identifyingName).toList(), rulesetNames);
        return Set.copyOf(rulesets);
    }

    public ImmutableRuleset createRuleset(ImmutableRuleset ruleset) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO ruleset(owner_id, category, identifying_name, description, "type")
                     VALUES (?, ?::ruleset_category, ?, ?, ?)
                  RETURNING id, public_id, owner_id, category, identifying_name, description, "type";
                """,
            RowMappers.RULESET,
            ruleset.ownerId(), ruleset.category().fieldName(), ruleset.identifyingName(), ruleset.description(), ruleset.type().fieldName());
    }

    public Optional<Ruleset> findByName(String rulesetName) {
        try {
            return Optional.ofNullable(rulesetNameCache.get(rulesetName, r -> jdbcTemplate.queryForObject("""
                    SELECT id, public_id, owner_id, category, identifying_name, description, "type"
                      FROM ruleset
                     WHERE identifying_name = ?
                    """,
                RowMappers.RULESET,
                rulesetName)));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void deleteRuleset(ImmutableRuleset rule) {
        jdbcTemplate.update("DELETE FROM ruleset WHERE public_id = ?", rule.publicId());
    }

    private Cache warmup(Cache<String, Ruleset> rulesetNameCache) {
        jdbcTemplate.query("SELECT * FROM ruleset", RowMappers.RULESET).forEach(ruleset -> {
            rulesetNameCache.put(ruleset.identifyingName(), ruleset);
        });
        return rulesetNameCache;
    }

}
