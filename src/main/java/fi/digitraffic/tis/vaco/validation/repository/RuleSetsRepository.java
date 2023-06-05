package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class RuleSetsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleSetsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public RuleSetsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<ValidationRule> findRulesets(String businessId) {
        List<ImmutableValidationRule> ruleSets = jdbcTemplate.query("""
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
        LOGGER.info("Found {} rulesets for {}: {}", ruleSets.size(), businessId, ruleSets.stream().map(ValidationRule::identifyingName).toList());
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
}
