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

    public Optional<PartnershipRecord> findByIds(PartnershipType type,
                                                 CompanyRecord partnerA,
                                                 CompanyRecord partnerB) {
        return jdbc.query(
            """
            SELECT p.*
              FROM partnership p
             WHERE p.type = ?::partnership_type
               AND p.partner_a_id = ?
               AND p.partner_b_id = ?
            """,
            RowMappers.PARTNERSHIP_RECORD,
            type.fieldName(),
            partnerA.id(),
            partnerB.id()).stream().findFirst();
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
