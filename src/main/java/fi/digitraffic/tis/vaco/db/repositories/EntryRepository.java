package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

@Repository
public class EntryRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public EntryRepository(JdbcTemplate jdbc,
                           NamedParameterJdbcTemplate namedJdbc,
                           ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Transactional
    public Optional<EntryRecord> create(Entry entry, Optional<ContextRecord> context, Optional<CredentialsRecord> credentials) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                INSERT INTO entry(business_id, format, url, etag, metadata, name, notifications, context_id, credentials_id, send_notifications)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  RETURNING id,
                            public_id,
                            business_id,
                            format,
                            url,
                            etag,
                            metadata,
                            created,
                            started,
                            updated,
                            completed,
                            name,
                            notifications,
                            status,
                            context_id,
                            credentials_id,
                            send_notifications
                """,
                RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
                entry.businessId(),
                entry.format(),
                entry.url(),
                entry.etag(),
                RowMappers.writeJson(objectMapper, entry.metadata()),
                entry.name(),
                ArraySqlValue.create(entry.notifications().toArray(new String[0])),
                context.map(ContextRecord::id).orElse(null),
                credentials.map(CredentialsRecord::id).orElse(null),
                entry.sendNotifications()));
        } catch (DataAccessException dae) {
            logger.warn("Failed to create Entry", dae);
            return Optional.empty();
        }
    }

    public List<ValidationInput> createValidationInputs(EntryRecord entry, List<ValidationInput> validations) {
        return createValidationInputs(entry.id(), validations);
    }

    private List<ValidationInput> createValidationInputs(Long entryId, List<ValidationInput> validations) {
        if (validations == null) {
            return List.of();
        }
        return Streams.map(validations, validation -> jdbc.queryForObject(
                "INSERT INTO validation_input (entry_id, name, config) VALUES (?, ?, ?) RETURNING id, entry_id, name, config",
                RowMappers.VALIDATION_INPUT.apply(objectMapper),
                entryId, validation.name(), RowMappers.writeJson(objectMapper, validation.config())))
            .toList();
    }

    public List<ConversionInput> createConversionInputs(EntryRecord result, List<ConversionInput> conversions) {
        return createConversionInputs(result.id(), conversions);
    }

    private List<ConversionInput> createConversionInputs(Long entryId, List<ConversionInput> conversions) {
        if (conversions == null) {
            return List.of();
        }
        return Streams.map(conversions, validation -> jdbc.queryForObject(
                "INSERT INTO conversion_input (entry_id, name, config) VALUES (?, ?, ?) RETURNING id, entry_id, name, config",
                RowMappers.CONVERSION_INPUT.apply(objectMapper),
                entryId, validation.name(), RowMappers.writeJson(objectMapper, validation.config())))
            .toList();
    }

    public Optional<EntryRecord> findByPublicId(String publicId) {
        return findEntry(publicId);
    }

    private Optional<EntryRecord> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                        SELECT id,
                               public_id,
                               business_id,
                               format,
                               url,
                               etag,
                               metadata,
                               created,
                               started,
                               updated,
                               completed,
                               name,
                               notifications,
                               status,
                               context_id,
                               credentials_id,
                               send_notifications
                          FROM entry qe
                         WHERE qe.public_id = ?
                        """,
                        RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
                        publicId));
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("Failed to find entry", erdae);
            return Optional.empty();
        }
    }

    public void startEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET started=NOW(),
                       updated=NOW()
                 WHERE public_id = ?
                """,
                entry.publicId());
    }

    public void updateEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW()
                 WHERE public_id = ?
                """,
                entry.publicId());
    }

    public void completeEntryProcessing(Entry entry) {
        jdbc.update("""
                UPDATE entry
                   SET updated=NOW(),
                       completed=NOW()
                 WHERE public_id = ?
                """,
                entry.publicId());
    }

    public void markStatus(Entry entry, Status status) {
        jdbc.update("""
               UPDATE entry
                  SET status = (?)::status
                WHERE public_id = ?
            """,
            status.fieldName(),
            entry.publicId());
    }

    public void markStatus(EntryRecord entry, Status status) {
        jdbc.update("""
               UPDATE entry
                  SET status = (?)::status
                WHERE id = ?
            """,
            status.fieldName(),
            entry.id());
    }

    public List<EntryRecord> findAllByBusinessId(String businessId) {
        return findAllByBusinessId(businessId, OptionalInt.empty(), Optional.empty());
    }

    public List<EntryRecord> findAllByBusinessId(String businessId, OptionalInt count, Optional<String> name) {
        Object[] params;
        String sql;

        if (count.isEmpty() && name.isEmpty()) {
            // Count and name not provided
            sql = "SELECT * FROM entry WHERE business_id = ? ORDER BY created DESC";
            params = new Object[] {businessId};
        } else if (count.isEmpty()) {
            // Only name provided
            sql = "SELECT * FROM entry WHERE business_id = ? AND name = ? ORDER BY created DESC";
            params = new Object[] {businessId, name.get()};
        } else if (name.isEmpty()) {
            // Only count provided
            sql = "SELECT * FROM entry WHERE business_id = ? ORDER BY created DESC LIMIT ?";
            params = new Object[] {businessId, count.getAsInt()};
        } else {
            // Both count and name provided
            sql = "SELECT * FROM entry WHERE business_id = ? AND name = ? ORDER BY created DESC LIMIT ?";
            params = new Object[] {businessId, name.get(), count.getAsInt()};
        }
        try {
            return jdbc.query(sql, RowMappers.PERSISTENT_ENTRY.apply(objectMapper), params);
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("Failed to find all by business id", erdae);
            return List.of();
        }
    }

    /**
     * !! This is used by UI MyData page only
     * @param businessId
     * @return
     */
    public List<EntryRecord> findLatestForBusinessId(String businessId) {
        try {
            return namedJdbc.query("""
                SELECT e.*
                  FROM (SELECT e.*,
                               ROW_NUMBER() OVER (PARTITION BY url ORDER BY created DESC) r
                          FROM entry e
                         WHERE e.business_id = :businessId) AS e
                 WHERE e.r <= 10
                ORDER BY created DESC;
                """,
                new MapSqlParameterSource()
                    .addValue("businessId", businessId),
                RowMappers.PERSISTENT_ENTRY.apply(objectMapper));
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("Failed to find all for business ids", erdae);
            return List.of();
        }
    }

    public Optional<EntryRecord> findLatestForBusinessIdAndContext(String businessId, String context) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                SELECT *
                  FROM entry e
                 WHERE e.business_id = ?
                   AND e.context_id = (SELECT id
                                         FROM context c
                                        WHERE c.company_id = (SELECT id
                                                                FROM company co
                                                               WHERE co.business_id = e.business_id)
                                          AND c.context = ?)
                 AND e.status NOT IN ('received', 'processing')
                 ORDER BY completed DESC
                 LIMIT 1
                """,
                RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
                businessId,
                context));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public boolean updateEtag(Entry entry, String etag) {
        return jdbc.update(
            """
            UPDATE entry
               SET etag = ?
             WHERE public_id = ?
            """,
            etag,
            entry.publicId()
        ) == 1;
    }

    public Optional<Status> findEntryStatus(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT status
                  FROM entry
                 WHERE public_id = ?
                """,
                RowMappers.STATUS,
                publicId));
        } catch (DataAccessException dae) {
            logger.warn("Failed to fetch status of entry {}", publicId);
            return Optional.empty();
        }
    }

    /**
     * Removes entries which are older than specified, keeping at least specified amount of entries for each url.
     *
     * @param olderThan           Age limit for entries
     * @param keepAtLeast         How many entries should be kept at minimum
     * @param removeAtMostInTotal How many entries should be deleted by a single run at most
     * @return Public id:s of entries removed by history cleanup.
     */
    public List<String> cleanupHistory(Duration olderThan, int keepAtLeast, int removeAtMostInTotal) {
        try {
            return jdbc.queryForList(
                """
                   DELETE
                     FROM entry e
                    WHERE id IN (SELECT e.id AS id
                                   FROM (SELECT e.id,
                                                e.created,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created DESC) newest_to_oldest
                                           FROM entry e) AS e
                                  WHERE e.newest_to_oldest > ?
                                     OR e.created < NOW() - ?
                                  LIMIT ?)
                RETURNING e.public_id;
                """,
                String.class,
                keepAtLeast, RowMappers.writeInterval(olderThan), removeAtMostInTotal
            );
        } catch (DataAccessException dae) {
            logger.warn("Failed to cleanup entry history, returning empty list", dae);
            return List.of();
        }
    }

    /**
     * Compression removes in-between entries which bear no significant value.
     * <ul>
     *     <li>only keep first and latest {@link fi.digitraffic.tis.vaco.entries.model.Status#CANCELLED} entry for each feed</li>
     * </ul>
     * @return Public id:s of entries removed by compression.
     */
    public List<String> compressHistory() {
        try {
            return jdbc.queryForList(
                """
                   DELETE
                     FROM entry e
                    WHERE id IN (SELECT e.id AS id
                                   FROM (SELECT e.id,
                                                e.created,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created DESC) newest_to_oldest,
                                                ROW_NUMBER() OVER (PARTITION BY e.context_id ORDER BY created ASC) oldest_to_newest
                                           FROM entry e
                                          WHERE e.status = 'cancelled') AS e
                                  WHERE e.newest_to_oldest != 1
                                    AND e.oldest_to_newest != 1)
                RETURNING e.public_id;
                """,
                String.class
            );
        } catch (DataAccessException dae) {
            logger.warn("Failed to cleanup entry history, returning empty list", dae);
            return List.of();
        }
    }

    public List<String> cleanEntriesWithoutContext(Duration olderThan) {
        try {
            return jdbc.queryForList(
                """
                  DELETE
                  FROM entry
                  WHERE created < NOW() - ?
                    AND context_id IS NULL
                  RETURNING public_id
                """,
                String.class,
                RowMappers.writeInterval(olderThan)
            );
        } catch (DataAccessException dae) {
            logger.warn("Failed to cleanup entry history, returning empty list", dae);
            return List.of();
        }

    }

    public boolean updateEntry(Entry entry, Long credentialsId) {
        return jdbc.update(
            """
            UPDATE entry
               SET credentials_id = ?
             WHERE public_id = ?
            """,
            credentialsId,
            entry.publicId()
        ) == 1;

    }
}
