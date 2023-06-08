package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public class RuleSetsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RuleSetsRepository(JdbcTemplate jdbcTemplate,
                              NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<ValidationRule> findRulesets(String businessId) {
        return Set.copyOf(jdbcTemplate.query("""
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
                businessId));
    }

    public Set<ValidationRule> findRulesets(String businessId, Set<String> ruleNames) {
        if (ruleNames.isEmpty()) {
            return findRulesets(businessId);
        }
        return Set.copyOf(namedParameterJdbcTemplate.query("""
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
                RowMappers.RULESET));
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


    public void deleteRuleSet(ImmutableValidationRule rule) {
        jdbcTemplate.update("DELETE FROM validation_ruleset WHERE public_id = ?", rule.publicId());
    }
}
