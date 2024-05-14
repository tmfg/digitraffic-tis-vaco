package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.ConversionInputRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.EntryRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class ConversionInputRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConversionInputRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<ConversionInputRecord> create(EntryRecord entry, List<ConversionInput> conversions) {
        if (conversions == null) {
            return List.of();
        }
        return Streams.map(conversions, conversion -> jdbc.queryForObject("""
                INSERT INTO conversion_input (entry_id, name, config)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, config
                """,
                RowMappers.CONVERSION_INPUT_RECORD.apply(objectMapper),
                entry.id(),
                conversion.name(),
                RowMappers.writeJson(objectMapper, conversion.config())))
            .toList();
    }


}
