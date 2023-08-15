package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;

public class RowMappers {

    private static final Logger LOGGER = LoggerFactory.getLogger(RowMappers.class);

    public static final RowMapper<ImmutableRuleset> RULESET = (rs, rowNum) -> ImmutableRuleset.builder()
            .id(rs.getLong("id"))
            .publicId(rs.getString("public_id"))
            .ownerId(rs.getLong("owner_id"))
            .identifyingName(rs.getString("identifying_name"))
            .description(rs.getString("description"))
            .category(Category.forField(rs.getString("category")))
            .type(Type.forField(rs.getString("type")))
            .build();

    public static final RowMapper<ImmutablePhase> PHASE = (rs, rowNum) -> ImmutablePhase.builder()
            .id(rs.getLong("id"))
            .entryId(rs.getLong("entry_id"))
            .name(rs.getString("name"))
            .priority(rs.getInt("priority"))
            .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
            .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
            .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
            .build();

    public static final Function<String, RowMapper<ImmutableOrganization>> ALIASED_ORGANIZATION = (alias) ->
        (rs, rowNum) -> ImmutableOrganization.builder()
            .id(rs.getLong(alias + "id"))
            .publicId(rs.getString(alias + "public_id"))
            .businessId(rs.getString(alias + "business_id"))
            .name(rs.getString(alias + "name"))
            .build();

    public static final RowMapper<ImmutableOrganization> ORGANIZATION = ALIASED_ORGANIZATION.apply("");

    public static final RowMapper<ImmutableCooperation> COOPERATION = (rs, rowNum) -> ImmutableCooperation.builder()
            .cooperationType(CooperationType.forField(rs.getString("type")))
            .partnerA(ALIASED_ORGANIZATION.apply("partner_a_").mapRow(rs, rowNum))
            .partnerB(ALIASED_ORGANIZATION.apply("partner_b_").mapRow(rs, rowNum))
            .build();

    public static final Function<ObjectMapper, RowMapper<ImmutableEntry>> QUEUE_ENTRY = RowMappers::mapQueueEntry;
    public static final Function<ObjectMapper, RowMapper<ImmutableValidationInput>> VALIDATION_INPUT = RowMappers::mapValidationInput;
    public static final Function<ObjectMapper, RowMapper<ImmutableError>> ERROR = RowMappers::mapError;

    private static RowMapper<ImmutableError> mapError(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            ImmutableError.Builder b = ImmutableError.builder()
                    .id(rs.getLong("id"))
                    .publicId(rs.getString("public_id"))
                    .phaseId(rs.getLong("phase_id"))
                    .rulesetId(rs.getLong("ruleset_id"))
                    .message(rs.getString("message"));
            try {
                b = b.raw(objectMapper.readTree(rs.getBytes("raw")));  // TODO: this is byte[] while it could be JSONB
            } catch (IOException e) {
                LOGGER.warn("Failed to read field 'raw' of Error row. Not JSON?", e);
            }
            return b.build();
        };
    }

    private static RowMapper<ImmutableEntry> mapQueueEntry(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableEntry.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getString("public_id"))
                .businessId(rs.getString("business_id"))
                .format(rs.getString("format"))
                .url(rs.getString("url"))
                .etag(rs.getString("etag"))
                .metadata(readJson(objectMapper, rs, "metadata"))
                .created(nullable(rs.getTimestamp("created"), Timestamp::toLocalDateTime))
                .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
                .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
                .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
                .build();
    }

    private static RowMapper<ImmutableValidationInput> mapValidationInput(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableValidationInput.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .config(readJson(objectMapper, rs, "config"))
            .build();
    }

    private static <I,O> O nullable(I input, Function<I, O> i2o) {
        return Optional.ofNullable(input).map(i2o).orElse(null);
    }

    private static JsonNode readJson(ObjectMapper objectMapper, ResultSet rs, String field) {
        try {
            PGobject source = (PGobject) rs.getObject(field);
            if (source != null) {
                return objectMapper.readTree(source.getValue());
            }
        } catch (SQLException | JsonProcessingException e) {
            LOGGER.error("Failed Jdbc conversion from PGobject to JsonNode", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
    }

    public static PGobject writeJson(ObjectMapper objectMapper, JsonNode tree) {
        try {
            if (tree != null) {
                PGobject pgo = new PGobject();
                pgo.setType("jsonb");
                pgo.setValue(objectMapper.writeValueAsString(tree));
                return pgo;
            }
        } catch (SQLException | JsonProcessingException e) {
            LOGGER.error("Failed Jdbc conversion from JsonNode to PGobject", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
    }
}
