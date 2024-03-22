package fi.digitraffic.tis.vaco.db.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbc;

    public CompanyRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public boolean deleteByBusinessId(String businessId) {
        return jdbc.update("""
            DELETE FROM company
             WHERE business_id = ?
            """,
            businessId) == 1;
    }
}
