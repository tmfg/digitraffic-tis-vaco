package fi.digitraffic.tis.vaco.organization.repository;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CooperationRepository {

    private final JdbcTemplate jdbc;

    public CooperationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImmutableCooperation create(ImmutableCooperation cooperation) {
        return jdbc.queryForObject("""
                INSERT INTO cooperation(type, partner_a_id, partner_b_id)
                     VALUES (?::cooperation_type, ?, ?)
                  RETURNING type, partner_a_id, partner_b_id
                """,
            RowMappers.COOPERATION,
            cooperation.cooperationType().fieldName(), cooperation.partnerA(), cooperation.partnerB());
    }

    public Optional<ImmutableCooperation> findByIds(CooperationType type,
                                                    Long partnerAId,
                                                    Long partnerBId) {
        return jdbc.query("""
                SELECT * FROM cooperation
                    WHERE type = ?::cooperation_type AND partner_a_id = ? AND partner_b_id = ?
                """,
            RowMappers.COOPERATION,
            type.fieldName(), partnerAId, partnerBId).stream().findFirst();
    }
}
