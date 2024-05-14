package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.ValidationInputRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class ValidationInputRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ValidationInputRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<ValidationInputRecord> create(EntryRecord entry, List<ValidationInput> validations) {
        if (validations == null) {
            return List.of();
        }
        return Streams.map(validations, validation -> jdbc.queryForObject("""
                INSERT INTO validation_input (entry_id, name, config)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, config
                """,
                RowMappers.VALIDATION_INPUT_RECORD.apply(objectMapper),
                entry.id(),
                validation.name(),
                RowMappers.writeJson(objectMapper, validation.config())))
            .toList();
    }


}
