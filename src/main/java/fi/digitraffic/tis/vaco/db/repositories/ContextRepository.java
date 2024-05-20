package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ContextRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    private final CachingService cachingService;

    public ContextRepository(JdbcTemplate jdbc, CachingService cachingService) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public Optional<ContextRecord> upsert(Entry entry) {
        return cachingService.cacheContextRecord(entry.businessId() + "/" + entry.context(), key -> {
            if (entry.context() == null) {
                return null;
            }
            try {
                return jdbc.queryForObject("""
                    INSERT INTO context(company_id, context)
                         VALUES ((SELECT id FROM company WHERE business_id = ?), ?)
                    ON CONFLICT (company_id, context)
                             DO UPDATE SET context = EXCLUDED.context
                      RETURNING *
                    """,
                    RowMappers.CONTEXT_RECORD,
                    entry.businessId(),
                    entry.context());
            } catch (DataAccessException dae) {
                logger.warn("Failed to insert context", dae);
                return null;
            }
        });
    }

    public Optional<ContextRecord> find(EntryRecord entry) {
        if (entry.context() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT c.*
                  FROM context c
                 WHERE c.id = ?
                """,
                RowMappers.CONTEXT_RECORD,
                entry.context()));
        } catch (DataAccessException dae) {
            logger.warn("Failed to find context record for context %s/%s".formatted(entry.publicId(), entry.context()), dae);
            return Optional.empty();
        }
    }

    public Optional<ContextRecord> find(String context, Long companyId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT c.*
                  FROM context c
                 WHERE lower(trim(c.context)) = lower(trim(?)) AND c.company_id = ?
                """,
                RowMappers.CONTEXT_RECORD,
                context,
                companyId));
        } catch (DataAccessException dae) {
            logger.warn("Failed to find context record for companyId/context %s/%s".formatted(companyId, context), dae);
            return Optional.empty();
        }
    }

    public List<ContextRecord> findByBusinessId(String businessId) {
        return jdbc.query("""
                SELECT c.*
                  FROM context c
                 JOIN company ON company.id = c.company_id AND company.business_id = ?
                """,
            RowMappers.CONTEXT_RECORD,
            businessId);
    }

    public ContextRecord create(String context, Long companyId) {
        return jdbc.queryForObject(
            """
            INSERT INTO context(context, company_id)
                 VALUES (?, ?)
              RETURNING *
            """,
            RowMappers.CONTEXT_RECORD,
            context,
            companyId);
    }

    public ContextRecord update(String oldContext, String newContext, Long companyId) {
        return jdbc.queryForObject(
            """
            UPDATE context
                 SET context = ?
                 WHERE company_id = ? AND context = ?
              RETURNING *
            """,
            RowMappers.CONTEXT_RECORD,
            newContext,
            companyId,
            oldContext);
    }
}
