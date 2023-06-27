package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbc;

    public OrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImmutableOrganization create(ImmutableOrganization organization) {
        return jdbc.queryForObject("""
                INSERT INTO organization(business_id, name)
                     VALUES (?, ?)
                  RETURNING id, public_id, business_id, name
                """,
                RowMappers.ORGANIZATION,
                organization.businessId(), organization.name());
    }

    public ImmutableOrganization getByBusinessId(String businessId) {
        return jdbc.queryForObject("""
                SELECT *
                  FROM organization
                 WHERE business_id = ?
                """,
            RowMappers.ORGANIZATION,
            businessId);
    }

    public Optional<ImmutableOrganization> findByBusinessId(String businessId) {
        return jdbc.query("""
                SELECT *
                  FROM organization
                 WHERE business_id = ?
                """,
                RowMappers.ORGANIZATION,
                businessId).stream().findFirst();
    }

    public void delete(String businessId) {
        jdbc.update("DELETE FROM organization WHERE business_id = ?", businessId);
    }
}
