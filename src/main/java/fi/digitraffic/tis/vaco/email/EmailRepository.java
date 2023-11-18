package fi.digitraffic.tis.vaco.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class EmailRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EmailRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // TODO: reduce scope
    public List<ImmutableEntry> findLatestEntries(Company company) {
        return jdbc.query("""
            SELECT e.*
              FROM (SELECT e.*, ROW_NUMBER() OVER (PARTITION BY format ORDER BY created DESC) r
                      FROM entry e
                      WHERE e.business_id = ?) AS e
             WHERE e.r = 1
            """,
            RowMappers.QUEUE_ENTRY.apply(objectMapper),
            company.businessId());
    }
}
