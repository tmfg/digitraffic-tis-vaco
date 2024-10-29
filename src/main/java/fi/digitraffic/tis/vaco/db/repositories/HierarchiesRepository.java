package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.HierarchyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class HierarchiesRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    public HierarchiesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HierarchyRecord> listAll() {
        try {
            return jdbc.query(
                """
                SELECT * FROM hierarchy
                """,
                RowMappers.HIERARCHY_RECORD
            );
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    public Optional<HierarchyRecord> createHierarchy(CompanyRecord companyRecord, HierarchyType type) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                INSERT INTO hierarchy (root_company_id, type)
                     VALUES (?, ?::hierarchy_type)
                  RETURNING id,
                            public_id,
                            root_company_id,
                            type
                """,
                RowMappers.HIERARCHY_RECORD,
                companyRecord.id(),
                type.fieldName()));
        } catch (DataAccessException dae) {
            logger.warn("Failed to create Hierarchy", dae);
            return Optional.empty();
        }
    }
}
