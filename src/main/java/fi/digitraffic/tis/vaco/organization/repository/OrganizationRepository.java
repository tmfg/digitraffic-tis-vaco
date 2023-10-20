package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbc;

    public OrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Organization create(Organization organization) {
        return jdbc.queryForObject("""
                INSERT INTO organization(business_id, name)
                     VALUES (?, ?)
                  RETURNING id, business_id, name
                """,
                RowMappers.ORGANIZATION,
                organization.businessId(), organization.name());
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
}
