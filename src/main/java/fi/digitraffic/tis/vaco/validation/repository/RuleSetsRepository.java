package fi.digitraffic.tis.vaco.validation.repository;

import com.github.benmanes.caffeine.cache.Cache;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
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
public class RuleSetsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleSetsRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Cache<String, ValidationRule> rulesetNameCache;

    public RuleSetsRepository(JdbcTemplate jdbcTemplate,
                              NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              @Qualifier("rulesetNameCache") Cache<String, ValidationRule> rulesetNameCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.rulesetNameCache = warmup(rulesetNameCache);
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<ValidationRule> findRulesets(String businessId) {
        List<ImmutableValidationRule> ruleSets = jdbcTemplate.query("""
                WITH current_id AS (
                    SELECT id
                      FROM tis_organization
                     WHERE business_id = ?
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM tis_cooperation, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT vr.*
                  FROM validation_ruleset vr, current_id
                 WHERE vr.owner_id = current_id.id
                UNION
                SELECT DISTINCT vr.*
                  FROM validation_ruleset vr, parents
                 WHERE vr.owner_id IN (parents.id)
                   AND vr.category = 'generic'
                """,
                RowMappers.RULESET,
                businessId);
        LOGGER.info("Found {} rulesets for {}: {}", ruleSets.size(), businessId, ruleSets.stream().map(ValidationRule::identifyingName).toList());
        return Set.copyOf(ruleSets);
    }

    public Set<ValidationRule> findRulesets(String businessId, Set<String> ruleNames) {
        if (ruleNames.isEmpty()) {
            return findRulesets(businessId);
        }
        List<ImmutableValidationRule> ruleSets = namedParameterJdbcTemplate.query("""
                WITH current_id AS (
                    SELECT id
                      FROM tis_organization
                     WHERE business_id = :businessId
                ),
                specific_rules AS (
                    SELECT id
                      FROM validation_ruleset
                     WHERE identifying_name IN (:ruleNames)
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM tis_cooperation, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT vr.*
                  FROM validation_ruleset vr, current_id, specific_rules
                 WHERE vr.owner_id = current_id.id
                   AND vr.id IN (specific_rules.id)
                UNION
                SELECT DISTINCT vr.*
                  FROM validation_ruleset vr, parents, specific_rules
                 WHERE vr.owner_id IN (parents.id)
                   AND vr.category = 'generic'
                """,
                new MapSqlParameterSource()
                        .addValue("businessId", businessId)
                        .addValue("ruleNames", ruleNames),
                RowMappers.RULESET);
        LOGGER.info("Found {} rulesets for {}: resolved {}, requested {}", ruleSets.size(), businessId, ruleSets.stream().map(ValidationRule::identifyingName).toList(), ruleNames);
        return Set.copyOf(ruleSets);
    }

    public ImmutableValidationRule createRuleSet(ImmutableValidationRule ruleSet) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO validation_ruleset(owner_id, category, identifying_name, description)
                     VALUES (?, ?::validation_ruleset_category, ?, ?)
                  RETURNING id, public_id, owner_id, category, identifying_name, description;
                """,
                RowMappers.RULESET,
                ruleSet.ownerId(), ruleSet.category().fieldName(), ruleSet.identifyingName(), ruleSet.description());
    }

    public Optional<ValidationRule> findByName(String ruleName) {
        try {
            return Optional.ofNullable(rulesetNameCache.get(ruleName, r -> jdbcTemplate.queryForObject("""
                    SELECT id, public_id, owner_id, category, identifying_name, description
                      FROM validation_ruleset
                     WHERE identifying_name = ?
                    """,
                RowMappers.RULESET,
                ruleName)));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void deleteRuleSet(ImmutableValidationRule rule) {
        jdbcTemplate.update("DELETE FROM validation_ruleset WHERE public_id = ?", rule.publicId());
    }

    private Cache warmup(Cache<String, ValidationRule> rulesetNameCache) {
        jdbcTemplate.query("SELECT * FROM validation_ruleset", RowMappers.RULESET).forEach(ruleset -> {
            rulesetNameCache.put(ruleset.identifyingName(), ruleset);
        });
        return rulesetNameCache;
    }

}
