package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * Manages explicit ruleset access grants (ruleset_access table). This is the sole mechanism by which any company
 * (including Fintraffic itself) gets direct access to a ruleset; see {@link RulesetRepository#findRulesets(String)}
 * for how these grants are resolved together with the single-hop, category-gated partnership inheritance.
 */
@Repository
public class RulesetAccessRepository {

    private final JdbcTemplate jdbc;

    public RulesetAccessRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /**
     * Grants a company access to a ruleset. Idempotent — returns {@code false} without altering anything if the
     * grant already exists.
     */
    public boolean grantAccess(CompanyRecord company, RulesetRecord ruleset) {
        if (hasGrant(company, ruleset)) {
            return false;
        }
        jdbc.update(
            """
            INSERT INTO ruleset_access (company_id, ruleset_id)
                 VALUES (?, ?)
            """,
            company.id(),
            ruleset.id());
        return true;
    }

    public void revokeAccess(CompanyRecord company, RulesetRecord ruleset) {
        jdbc.update(
            "DELETE FROM ruleset_access WHERE company_id = ? AND ruleset_id = ?",
            company.id(),
            ruleset.id());
    }

    public boolean hasGrant(CompanyRecord company, RulesetRecord ruleset) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM ruleset_access WHERE company_id = ? AND ruleset_id = ?",
            Integer.class,
            company.id(),
            ruleset.id());
        return count != null && count > 0;
    }

    /**
     * Lists all companies directly granted access to the given ruleset (does not include companies which only see
     * the ruleset via partnership inheritance).
     */
    public List<CompanyRecord> findGrantsForRuleset(RulesetRecord ruleset) {
        return jdbc.query(
            """
            SELECT c.*
              FROM company c
              JOIN ruleset_access ra ON ra.company_id = c.id
             WHERE ra.ruleset_id = ?
            """,
            RowMappers.COMPANY_RECORD,
            ruleset.id());
    }
}

