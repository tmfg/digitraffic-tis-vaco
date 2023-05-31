package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.vaco.queuehandler.repository.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRuleSet;
import fi.digitraffic.tis.vaco.validation.model.RuleSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class RulesetsRepository {

    private final JdbcTemplate jdbcTemplate;

    public RulesetsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<RuleSet> findRulesets(String businessId) {
        List<ImmutableRuleSet> ruleSets = jdbcTemplate.query("""
                WITH partnering_business AS (
                    SELECT ? AS business_id
                ),
                cooperating_businesses AS (
                    SELECT id
                      FROM tis_organization org, partnering_business pb
                     WHERE org.business_id = pb.business_id
                        OR id IN (SELECT partner_a_id
                                    FROM tis_cooperation
                                   WHERE partner_b_id = (SELECT id FROM tis_organization WHERE business_id = pb.business_id))
                )
                SELECT DISTINCT vr.* FROM validation_ruleset vr, cooperating_businesses cb WHERE vr.owner_id IN (cb.id)
                """,
                RowMappers.RULESET,
                businessId);
        return Set.copyOf(ruleSets);
    }

    public ImmutableRuleSet createRuleSet(ImmutableRuleSet ruleSet) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO validation_ruleset(owner_id, category, identifying_name, description)
                     VALUES (?, ?::validation_ruleset_category, ?, ?)
                  RETURNING id, public_id, owner_id, category, identifying_name, description;
                """,
                RowMappers.RULESET,
                ruleSet.ownerId(), ruleSet.category().fieldName(), ruleSet.identifyingName(), ruleSet.description());
    }
}
