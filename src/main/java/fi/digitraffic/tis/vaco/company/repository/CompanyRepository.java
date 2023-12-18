package fi.digitraffic.tis.vaco.company.repository;

import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CompanyRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public Company create(Company company) {
        return jdbc.queryForObject("""
                INSERT INTO company(business_id, name, contact_emails)
                     VALUES (?, ?, ?)
                  RETURNING *
                """,
                RowMappers.COMPANY,
                company.businessId(),
            company.name(),
            ArraySqlValue.create(company.contactEmails().toArray(new String[0])));
    }

    public Optional<Company> findByBusinessId(String businessId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM company
                     WHERE business_id = ?
                    """,
                RowMappers.COMPANY,
                businessId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void delete(String businessId) {
        jdbc.update("DELETE FROM company WHERE business_id = ?", businessId);
    }

    public List<Company> listAllWithEntries() {
        return jdbc.query("""
            SELECT *
              FROM company o
             WHERE o.business_id IN (SELECT DISTINCT e.business_id
                                       FROM entry e)
            """,
            RowMappers.COMPANY);
    }

    public Set<Company> findAllByAdGroupIds(List<String> adGroupIds) {
        return Set.copyOf(namedJdbc.query("""
            SELECT DISTINCT *
              FROM company c
             WHERE ad_group_id IN (:adGroupIds)
            """,
            new MapSqlParameterSource()
                .addValue("adGroupIds", adGroupIds),
            RowMappers.COMPANY));
    }
}
