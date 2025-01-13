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

/**
 * @deprecated Admin tools is not a concept anymore in VACO codebase. Use table specific repositories instead.
 */
@Deprecated(since = "2024-08-29")
@Repository
public class AdminToolsRepository {

    private final JdbcTemplate jdbc;

    private final NamedParameterJdbcTemplate namedJdbc;

    public AdminToolsRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public List<CompanyLatestEntry> getDataDeliveryOverview(Set<String> businessIds) {
        return namedJdbc.query(
            """
             WITH partitioned_entries AS (SELECT e.business_id,
                                                 e.url,
                                                 e.public_id,
                                                 e.id,
                                                 e.name,
                                                 e.format AS input_format,
                                                 e.status,
                                                 e.created,
                                                 ROW_NUMBER() OVER (PARTITION BY business_id, context_id, url, format ORDER BY created DESC) r,
                                                 e.context_id
                                            FROM entry e),
                  latest_entry AS (SELECT *
                                     FROM partitioned_entries pe
                                    WHERE pe.r = 1)
                           SELECT c.name AS company_name,
                  c.business_id,
                  latest_entry.public_id,
                  latest_entry.name,
                  (SELECT c.context FROM context c WHERE id = latest_entry.context_id) AS context,
                  COALESCE(latest_entry.url, 'NO_DATA') AS source_url,
                  latest_entry.status,
                  latest_entry.created,
                  CASE
                      WHEN latest_entry.input_format = 'netex' THEN 'NeTEx'
                      ELSE UPPER(latest_entry.input_format)
                   END AS input_format,
                  STRING_AGG(CASE
                                 WHEN starts_with(ci.name, 'gtfs2netex.fintraffic') THEN 'NeTEx'
                                 WHEN starts_with(ci.name, 'netex2gtfs.entur') THEN 'GTFS'
                                 ELSE '-'
                                 END, ' / ')            AS converted_format
             FROM company c
                      LEFT JOIN latest_entry ON c.business_id = latest_entry.business_id
                      LEFT JOIN conversion_input ci ON ci.entry_id = latest_entry.id
            WHERE (:businessIds)::TEXT IS NULL
               OR c.business_id IN (:businessIds)
            AND created >= NOW() - INTERVAL '3 months'
            GROUP BY company_name, c.business_id, public_id, latest_entry.name, latest_entry.context_id, source_url, input_format, status, created
            ORDER BY company_name, latest_entry.
               created DESC
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
