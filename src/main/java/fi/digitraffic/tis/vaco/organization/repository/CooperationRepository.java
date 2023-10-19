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
        int success = jdbc.update("""
                INSERT INTO cooperation(type, partner_a_id, partner_b_id)
                     VALUES (?::cooperation_type, ?, ?)
                """,
            cooperation.cooperationType().fieldName(), cooperation.partnerA().id(), cooperation.partnerB().id());
        // There's no easy way to do this with just one query so the fetch query is delegated to #findByIds to avoid
        // code duplication. The blind call to Optional#get is on purpose.
        return findByIds(cooperation.cooperationType(), cooperation.partnerA().id(), cooperation.partnerB().id()).get();
    }

    public Optional<ImmutableCooperation> findByIds(CooperationType type,
                                                    Long partnerAId,
                                                    Long partnerBId) {
        return jdbc.query("""
                SELECT c.type AS type,
                       o_a.id as partner_a_id,
                       o_a.business_id as partner_a_business_id,
                       o_a.name as partner_a_name,
                       o_b.id as partner_b_id,
                       o_b.business_id as partner_b_business_id,
                       o_b.name as partner_b_name
                  FROM cooperation c
                  JOIN organization o_a ON c.partner_a_id = o_a.id
                  JOIN organization o_b ON c.partner_b_id = o_b.id
                 WHERE c.type = ?::cooperation_type AND c.partner_a_id = ? AND c.partner_b_id = ?
                """,
            RowMappers.COOPERATION,
            type.fieldName(), partnerAId, partnerBId).stream().findFirst();
    }
}
