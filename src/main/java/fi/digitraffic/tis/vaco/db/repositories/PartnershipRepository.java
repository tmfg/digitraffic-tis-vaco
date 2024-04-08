package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.PartnershipRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class PartnershipRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public PartnershipRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public Optional<Partnership> findByIds(PartnershipType type,
                                           CompanyRecord partnerA,
                                           CompanyRecord partnerB) {
        return jdbc.query("""
                SELECT c.type AS type,
                       o_a.id as partner_a_id,
                       o_a.business_id as partner_a_business_id,
                       o_a.name as partner_a_name,
                       o_a.contact_emails as partner_a_contact_emails,
                       o_a.ad_group_id as partner_a_ad_group_id,
                       o_a.language as partner_a_language,
                       o_b.id as partner_b_id,
                       o_b.business_id as partner_b_business_id,
                       o_b.name as partner_b_name,
                       o_b.contact_emails as partner_b_contact_emails,
                       o_b.ad_group_id as partner_b_ad_group_id,
                       o_b.language as partner_b_language
                  FROM partnership c
                  JOIN company o_a ON c.partner_a_id = o_a.id
                  JOIN company o_b ON c.partner_b_id = o_b.id
                 WHERE c.type = ?::partnership_type AND c.partner_a_id = ? AND c.partner_b_id = ?
                """,
            RowMappers.PARTNERSHIP,
            type.fieldName(), partnerA.id(), partnerB.id()).stream().findFirst();
    }

    public PartnershipRecord create(PartnershipType type, CompanyRecord partnerA, CompanyRecord partnerB) {
        return jdbc.queryForObject(
            """
            INSERT INTO partnership(type, partner_a_id, partner_b_id)
                 VALUES (?::partnership_type, ?, ?)
              RETURNING *
            """,
            RowMappers.PARTNERSHIP_RECORD,
            type.fieldName(),
            partnerA.id(),
            partnerB.id());
    }

    public void deletePartnership(Partnership partnership) {
        jdbc.update(
            """
            DELETE FROM partnership
             WHERE type = ?::partnership_type
               AND partner_a_id = (SELECT id FROM company WHERE business_id = ?)
               AND partner_b_id = (SELECT id FROM company WHERE business_id = ?)
            """,
            partnership.type().fieldName(),
            partnership.partnerA().businessId(),
            partnership.partnerB().businessId());
    }

}
