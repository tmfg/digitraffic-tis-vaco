package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbc;

    public OrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Organization create(Organization organization) {
        return jdbc.queryForObject("""
                INSERT INTO organization(business_id, name, contact_emails)
                     VALUES (?, ?, ?)
                  RETURNING id, business_id, name, contact_emails
                """,
                RowMappers.ORGANIZATION,
                organization.businessId(),
            organization.name(),
            ArraySqlValue.create(organization.contactEmails().toArray(new String[0])));
    }

    public Optional<Organization> findByBusinessId(String businessId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM organization
                     WHERE business_id = ?
                    """,
                RowMappers.ORGANIZATION,
                businessId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void delete(String businessId) {
        jdbc.update("DELETE FROM organization WHERE business_id = ?", businessId);
    }

    public List<Organization> listAllWithEntries() {
        return jdbc.query("""
            SELECT *
              FROM organization o
             WHERE o.business_id IN (SELECT DISTINCT e.business_id
                                       FROM entry e)
            """,
            RowMappers.ORGANIZATION);
    }
}
