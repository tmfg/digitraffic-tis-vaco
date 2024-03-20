package fi.digitraffic.tis.vaco.entries;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    public PersistentEntry create(Entry entry) {
        return jdbc.queryForObject("""
                INSERT INTO entry(business_id, format, url, etag, metadata, name, notifications)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                  RETURNING id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed, name, notifications, status
                """,
            RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
            entry.businessId(),
            entry.format(),
            entry.url(),
            entry.etag(),
            RowMappers.writeJson(objectMapper, entry.metadata()),
            entry.name(),
            ArraySqlValue.create(entry.notifications().toArray(new String[0])));
    }

    public List<ValidationInput> createValidationInputs(PersistentEntry entry, List<ValidationInput> validations) {
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

    public List<ConversionInput> createConversionInputs(PersistentEntry result, List<ConversionInput> conversions) {
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

    public Optional<PersistentEntry> findByPublicId(String publicId) {
        return findEntry(publicId);
    }

    private Optional<PersistentEntry> findEntry(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                        SELECT id, public_id, business_id, format, url, etag, metadata, created, started, updated, completed, name, notifications, status
                          FROM entry qe
                         WHERE qe.public_id = ?
                        """,
                        RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
                        publicId));
        } catch (EmptyResultDataAccessException erdae) {
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

    public List<PersistentEntry> findAllByBusinessId(String businessId) {
        try {
            return jdbc.query("""
                    SELECT *
                      FROM entry
                     WHERE business_id = ?
                     ORDER BY created DESC
                    """,
                RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
                businessId);
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<PersistentEntry> findAllForBusinessIds(Set<String> businessIds) {
        try {
            return namedJdbc.query("""
                SELECT e.*
                  FROM entry e
                 WHERE e.business_id IN (:businessIds)
                """,
                new MapSqlParameterSource()
                    .addValue("businessIds", businessIds),
                RowMappers.PERSISTENT_ENTRY.apply(objectMapper));
        } catch (EmptyResultDataAccessException erdae) {
            return List.of();
        }
    }

    public List<PersistentEntry> findLatestEntries(Company company) {
        return jdbc.query("""
            SELECT e.*
              FROM (SELECT e.*, ROW_NUMBER() OVER (PARTITION BY format ORDER BY created DESC) r
                      FROM entry e
                      WHERE e.business_id = ?) AS e
             WHERE e.r = 1
            """,
            RowMappers.PERSISTENT_ENTRY.apply(objectMapper),
            company.businessId());
    }
}
