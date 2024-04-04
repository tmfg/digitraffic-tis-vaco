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
                SELECT c.name as company_name,
                       c.business_id,
                       latest_entries.public_id,
                       latest_entries.name,
                       coalesce(latest_entries.url, 'NO_DATA') as source_url,
                       CASE
                           WHEN latest_entries.input_format = 'NETEX' THEN 'NeTEx'
                           ELSE latest_entries.input_format
                        END as input_format,
                       latest_entries.status,
                       latest_entries.created,
                       STRING_AGG(CASE
                                      WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                      WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                      ELSE '-'
                                      END, ' / ') as converted_format
                FROM company c
                LEFT JOIN (SELECT partitioned_entries.*
                    FROM (SELECT e.business_id,
                                 e.url,
                                 e.public_id,
                                 e.id,
                                 e.name,
                                 upper(trim(format)) as input_format,
                                 e.status,
                                 e.created, ROW_NUMBER() OVER (PARTITION BY business_id, url, upper(trim(format)) ORDER BY created DESC) r
                          FROM entry e) AS partitioned_entries
                    WHERE partitioned_entries.r = 1) AS latest_entries
                ON c.business_id = latest_entries.business_id
                LEFT JOIN conversion_input ci ON ci.entry_id = latest_entries.id
                WHERE (:businessIds)::text IS NULL OR c.business_id IN (:businessIds)
                GROUP BY company_name, c.business_id, public_id, latest_entries.name, source_url, input_format, status, created
                ORDER BY company_name, latest_entries.created DESC
                """,
            new MapSqlParameterSource()
                .addValue("businessIds", businessIds),
            RowMappers.DATA_DELIVERY);
    }

    public List<CompanyWithFormatSummary> getCompaniesWithFormats() {
        return jdbc.query("""
                SELECT c.name, c.business_id, string_agg(formats.format, ', ') as format_summary
                FROM company c
                LEFT JOIN (
                        (SELECT DISTINCT e.business_id,
                                 CASE
                                   WHEN upper(e.format) = 'GTFS' THEN 'GTFS'
                                   WHEN upper(e.format) = 'NETEX' THEN 'NeTEx'
                                   WHEN upper(e.format) = 'GBFS' THEN 'GBFS'
                                   ELSE upper(e.format)
                                 END as format
                         FROM entry e)
                        UNION
                        (
                            SELECT DISTINCT e.business_id,
                                CASE
                                  WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                  WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                ELSE 'Unknown format'
                                END as format
                            FROM entry e
                            JOIN conversion_input ci ON ci.entry_id = e.id
                        )
                    ) formats ON formats.business_id = c.business_id
                GROUP BY c.name, c.business_id
                ORDER BY c.name ASC;
                """,
            RowMappers.COMPANY_WITH_FORMATS);
    }
}
