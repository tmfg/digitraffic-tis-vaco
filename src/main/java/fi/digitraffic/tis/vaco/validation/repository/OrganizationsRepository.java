package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.vaco.queuehandler.repository.RowMappers;
import fi.digitraffic.tis.vaco.validation.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.validation.model.ImmutableOrganization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationsRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImmutableOrganization createOrganization(ImmutableOrganization organization) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tis_organization(business_id, name)
                     VALUES (?, ?)
                  RETURNING id, public_id, business_id, name
                """,
                RowMappers.ORGANIZATION,
                organization.businessId(), organization.name());
    }

    public ImmutableOrganization findByBusinessId(String businessId) {
        return jdbcTemplate.queryForObject("""
                SELECT *
                  FROM tis_organization
                 WHERE business_id = ?
                """,
                RowMappers.ORGANIZATION,
                businessId);
    }

    public ImmutableCooperation createCooperation(ImmutableCooperation cooperation) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tis_cooperation(type, partner_a_id, partner_b_id)
                     VALUES (?::tis_cooperation_type, ?, ?)
                  RETURNING type, partner_a_id, partner_b_id
                """,
                RowMappers.COOPERATION,
                cooperation.cooperationType().fieldName(), cooperation.partnerA(), cooperation.partnerB());
    }
}
