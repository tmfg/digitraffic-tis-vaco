package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class RulesetRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public RulesetRepository(JdbcTemplate jdbc,
                             NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public Set<Ruleset> findRulesets(String businessId) {
        List<Ruleset> rulesets = namedJdbc.query("""
                WITH current_id AS (
                    SELECT id
                      FROM company
                     WHERE business_id = :businessId
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM partnership, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT r.*
                  FROM ruleset r, current_id
                 WHERE r.owner_id = current_id.id
                UNION
                SELECT DISTINCT r.*
                  FROM ruleset r, parents
                 WHERE r.owner_id IN (parents.id)
                   AND r.category = 'generic'
                """,
            new MapSqlParameterSource()
                .addValue("businessId", businessId),
            RowMappers.RULESET);
        logger.info("Found {} rulesets for {}: {}", rulesets.size(), businessId, rulesets.stream().map(Ruleset::identifyingName).toList());
        return Set.copyOf(rulesets);
    }

    public Set<Ruleset> findRulesets(String businessId, TransitDataFormat format, Type type) {
        List<Ruleset> rulesets = namedJdbc.query("""
                WITH current_id AS (
                    SELECT id
                      FROM company
                     WHERE business_id = :businessId
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM partnership, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT r.*
                  FROM ruleset r, current_id
                 WHERE r.owner_id = current_id.id
                   AND r.type = :type
                   AND r.format = (:format)::transit_data_format
                UNION
                SELECT DISTINCT r.*
                  FROM ruleset r, parents
                 WHERE r.owner_id IN (parents.id)
                   AND r.category = 'generic'
                   AND r.type = :type
                   AND r.format = (:format)::transit_data_format
                """,
            new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("type", type.fieldName())
                .addValue("format", format.fieldName()),
            RowMappers.RULESET);
        logger.info("Found {} rulesets of type {} for {} of format {}: {}", rulesets.size(), type, businessId, format, rulesets.stream().map(Ruleset::identifyingName).toList());
        return Set.copyOf(rulesets);
    }

    public Set<Ruleset> findRulesets(String businessId, Type type, TransitDataFormat format, Set<String> rulesetNames) {
        if (rulesetNames.isEmpty()) {
            return findRulesets(businessId, format, type);
        }
        List<Ruleset> rulesets = namedJdbc.query("""
                WITH current_id AS (
                    SELECT id
                      FROM company
                     WHERE business_id = :businessId
                ),
                specific_rulesets AS (
                    SELECT id
                      FROM ruleset
                     WHERE identifying_name IN (:rulesetNames)
                       AND "type" = :type
                       AND format = (:format)::transit_data_format
                ),
                parents AS (
                    SELECT partner_a_id AS id
                      FROM partnership, current_id
                     WHERE partner_b_id = current_id.id
                )
                SELECT DISTINCT r.*
                  FROM ruleset r, current_id, specific_rulesets
                 WHERE r.owner_id = current_id.id
                   AND r.id IN (specific_rulesets.id)
                   AND r.type = :type
                   AND r.format = (:format)::transit_data_format
                UNION
                SELECT DISTINCT r.*
                  FROM ruleset r, parents, specific_rulesets
                 WHERE r.owner_id IN (parents.id)
                   AND r.category = 'generic'
                   AND r.type = :type
                   AND r.format = (:format)::transit_data_format
                """,
            new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("rulesetNames", rulesetNames)
                .addValue("type", type.fieldName())
                .addValue("format", format.fieldName()),
            RowMappers.RULESET);
        logger.debug("Found {} rulesets of type {} with format {} for {}: resolved {}, requested {}", rulesets.size(),
            type, format, businessId, rulesets.stream().map(Ruleset::identifyingName).toList(), rulesetNames);
        return Set.copyOf(rulesets);
    }

    public Ruleset createRuleset(Ruleset ruleset) {
        return jdbc.queryForObject(
                """
                INSERT INTO ruleset(owner_id, category, identifying_name, description, "type", format, before_dependencies, after_dependencies)
                     VALUES (?, ?::ruleset_category, ?, ?, ?, ?::transit_data_format, ?, ?)
                  RETURNING *;
                """,
                RowMappers.RULESET,
                ruleset.ownerId(),
                ruleset.category().fieldName(),
                ruleset.identifyingName(),
                ruleset.description(),
                ruleset.type().fieldName(),
                ruleset.format().fieldName(),
                ArraySqlValue.create(ruleset.beforeDependencies().toArray(new String[0])),
                ArraySqlValue.create(ruleset.afterDependencies().toArray(new String[0])));
    }

    public Optional<RulesetRecord> findByName(String rulesetName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM ruleset
                     WHERE identifying_name = ?
                    """,
                RowMappers.RULESET_RECORD,
                rulesetName));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void deleteRuleset(Ruleset rule) {
        jdbc.update("DELETE FROM ruleset WHERE public_id = ?", rule.publicId());
    }

    public Set<String> listAllNames() {
        return Set.copyOf(jdbc.queryForList("SELECT DISTINCT identifying_name FROM ruleset", String.class));
    }

    public boolean anyPrerequisiteDependencyFailed(Entry entry, Ruleset ruleset) {
        return Boolean.FALSE.equals(jdbc.queryForObject("""
            SELECT EXISTS(WITH entry AS (SELECT *
                                       FROM entry
                                      WHERE public_id = ?),
                           ruleset AS (SELECT *
                                         FROM ruleset
                                        WHERE id = ?)
                    SELECT
                      FROM task t,
                           entry e,
                           ruleset r
                     WHERE t.entry_id = e.id
                       AND t.status = ANY (ARRAY ['cancelled', 'failed']::status[])
                       AND t.name = ANY (r.before_dependencies))
            """,
            Boolean.class,
            entry.publicId(),
            ruleset.id()));
    }
}