package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Repository
public class AdminToolsRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;


    public AdminToolsRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public List<CompanyLatestEntry> listCompanyLatestEntries(Set<String> businessIds) {
        return namedJdbc.query("""
                SELECT c.name as company_name, c.business_id,
                       e.public_id,
                       CASE
                        WHEN latest_entries.format = 'NETEX' THEN 'NeTEx'
                        ELSE latest_entries.format
                       END as format,
                       latest_entries.converted_format,
                       e.status,
                       latest_entries.maxDate as created
                FROM company c
                LEFT JOIN ((SELECT entry.business_id,
                             upper(entry.format) as format,
                              CASE
                                WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                ELSE 'Unknown'
                              END as converted_format,
                             max(entry.created) maxDate
                        FROM entry
                        JOIN conversion_input ci ON ci.entry_id = entry.id
                        GROUP BY entry.business_id, format, converted_format)
                        UNION ALL
                        (
                          SELECT entry.business_id,
                                 upper(entry.format) as format,
                                 '-' converted_format,
                                 max(entry.created) maxDate
                          FROM entry
                          LEFT JOIN conversion_input ci ON ci.entry_id = entry.id
                          GROUP BY entry.business_id, upper(entry.format), converted_format)
                    ) latest_entries
                ON c.business_id = latest_entries.business_id
                LEFT JOIN entry e ON e.created = latest_entries.maxDate
                WHERE (:businessIds)::text IS NULL OR c.business_id IN (:businessIds)
                ORDER BY c.name, latest_entries.maxDate DESC
                """,
            new MapSqlParameterSource()
                .addValue("businessIds", businessIds),
            RowMappers.COMPANY_LATEST_ENTRY);
    }
}