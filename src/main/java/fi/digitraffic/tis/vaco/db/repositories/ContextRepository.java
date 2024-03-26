package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        return cachingService.cacheContextRecord(entry.publicId(), key -> {
            try {
                return jdbc.queryForObject("""
                    INSERT INTO context(company_id, context)
                         VALUES ((SELECT id FROM company WHERE business_id = ?), ?)
                    ON CONFLICT (company_id, context)
                             DO NOTHING
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

    public Optional<ContextRecord> find(PersistentEntry entry) {
        return cachingService.cacheContextRecord(entry.publicId(), key -> {
            if (entry.context() == null) {
                logger.debug("Entry {} does not have context set", entry.publicId());
                return null;
            }
            try {
                return jdbc.queryForObject("""
                    SELECT c.*
                      FROM context c
                     WHERE c.id = (SELECT context_id FROM entry WHERE public_id = ?)
                    """,
                    RowMappers.CONTEXT_RECORD,
                    entry.publicId());
            } catch (DataAccessException dae) {
                logger.warn("Failed to find context record", dae);
                return null;
            }
        });
    }
}
