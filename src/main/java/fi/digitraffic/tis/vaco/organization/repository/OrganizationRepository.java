package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImmutableOrganization create(ImmutableOrganization organization) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tis_organization(business_id, name)
                     VALUES (?, ?)
                  RETURNING id, public_id, business_id, name
                """,
                RowMappers.ORGANIZATION,
                organization.businessId(), organization.name());
    }

    public ImmutableOrganization getByBusinessId(String businessId) {
        return jdbcTemplate.queryForObject("""
                SELECT *
                  FROM tis_organization
                 WHERE business_id = ?
                """,
            RowMappers.ORGANIZATION,
            businessId);
    }

    public Optional<ImmutableOrganization> findByBusinessId(String businessId) {
        return jdbcTemplate.query("""
                SELECT *
                  FROM tis_organization
                 WHERE business_id = ?
                """,
                RowMappers.ORGANIZATION,
                businessId).stream().findFirst();
    }
}
