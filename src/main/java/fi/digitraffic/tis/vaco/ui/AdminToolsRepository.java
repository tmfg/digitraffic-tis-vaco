package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
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

    public List<CompanyLatestEntry> getDataDeliveryOverview(Set<String> businessIds) {
        return namedJdbc.query("""
                SELECT c.name as company_name, c.business_id,
                       e.public_id,
                       e.name,
                       coalesce(latest_entries.url, 'NO_DATA') as source_url,
                       CASE
                           WHEN latest_entries.format = 'NETEX' THEN 'NeTEx'
                           ELSE latest_entries.format
                       END as input_format,
                       e.status,
                       latest_entries.maxDate as created,
                       STRING_AGG(CASE
                                      WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                      WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                      ELSE ci.name
                                   END, ' / ') as converted_format
                FROM company c
                         LEFT JOIN (
                                        SELECT entry.business_id,
                                               entry.url,
                                               upper(entry.format) as format,
                                               max(entry.created) maxDate
                                        FROM vaco.vaco.entry
                                        GROUP BY entry.business_id, entry.url, upper(entry.format)
                                    ) latest_entries ON c.business_id = latest_entries.business_id
                         LEFT JOIN entry e ON e.created = latest_entries.maxDate
                         LEFT JOIN conversion_input ci ON ci.entry_id = e.id
                 WHERE (:businessIds)::text IS NULL OR c.business_id IN (:businessIds)
                 GROUP BY company_name, c.business_id, public_id, e.name, source_url, input_format, status, latest_entries.maxdate
                 ORDER BY c.name, latest_entries.maxDate DESC
                """,
            new MapSqlParameterSource()
                .addValue("businessIds", businessIds),
            RowMappers.DATA_DELIVERY);
    }

    public List<CompanyWithFormatSummary> getCompaniesWithFormats() {
        return jdbc.query("""
                SELECT c.name, c.business_id, string_agg(formats.format, ', ') as format_summary
                FROM vaco.vaco.company c
                LEFT JOIN (
                        (select distinct e.business_id,
                                 CASE
                                   WHEN upper(e.format) = 'GTFS' THEN 'GTFS'
                                   WHEN upper(e.format) = 'NETEX' THEN 'NeTEx'
                                   WHEN upper(e.format) = 'GBFS' THEN 'GBFS'
                                   ELSE upper(e.format)
                                 END as format
                         from  vaco.vaco.entry e)
                        UNION
                        (
                            select distinct e.business_id,
                                CASE
                                  WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                  WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                ELSE 'Unknown format'
                                END as format
                            from  vaco.vaco.entry e
                            JOIN vaco.vaco.conversion_input ci ON ci.entry_id = e.id
                        )
                    ) formats ON formats.business_id = c.business_id
                group by c.name, c.business_id
                ORDER BY c.name ASC;
                """,
            RowMappers.COMPANY_WITH_FORMATS);
    }
}
