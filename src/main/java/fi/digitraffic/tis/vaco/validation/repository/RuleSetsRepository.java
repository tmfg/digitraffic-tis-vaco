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
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RuleSetsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleSetsRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, ValidationRule> rulesetNameCache;

    public RuleSetsRepository(JdbcTemplate jdbcTemplate,
                              @Qualifier("rulesetNameCache") Cache<String, ValidationRule> rulesetNameCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.rulesetNameCache = warmup(rulesetNameCache);
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

    private Cache warmup(Cache<String, ValidationRule> rulesetNameCache) {
        jdbcTemplate.query("SELECT * FROM validation_ruleset", RowMappers.RULESET).forEach(ruleset -> {
            rulesetNameCache.put(ruleset.identifyingName(), ruleset);
        });
        return rulesetNameCache;
    }
}
