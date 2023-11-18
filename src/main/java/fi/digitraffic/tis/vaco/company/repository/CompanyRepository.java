package fi.digitraffic.tis.vaco.company.repository;

import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbc;

    public CompanyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Company create(Company company) {
        return jdbc.queryForObject("""
                INSERT INTO company(business_id, name, contact_emails)
                     VALUES (?, ?, ?)
                  RETURNING id, business_id, name, contact_emails
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
}
