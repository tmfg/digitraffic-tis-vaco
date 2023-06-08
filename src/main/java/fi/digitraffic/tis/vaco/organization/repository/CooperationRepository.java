package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CooperationRepository {

    private final JdbcTemplate jdbcTemplate;

    public CooperationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImmutableCooperation create(ImmutableCooperation cooperation) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tis_cooperation(type, partner_a_id, partner_b_id)
                     VALUES (?::tis_cooperation_type, ?, ?)
                  RETURNING type, partner_a_id, partner_b_id
                """,
            RowMappers.COOPERATION,
            cooperation.cooperationType().fieldName(), cooperation.partnerA(), cooperation.partnerB());
    }

    public Optional<ImmutableCooperation> findByIds(CooperationType type,
                                                    Long partnerAId,
                                                    Long partnerBId) {
        return jdbcTemplate.query("""
                SELECT * FROM tis_cooperation
                    WHERE type = ?::tis_cooperation_type AND partner_a_id = ? AND partner_b_id = ?
                """,
            RowMappers.COOPERATION,
            type.fieldName(), partnerAId, partnerBId).stream().findFirst();
    }
}
