package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;

public class RowMappers {

    private static final Logger LOGGER = LoggerFactory.getLogger(RowMappers.class);

    public static final RowMapper<Ruleset> RULESET = (rs, rowNum) -> ImmutableRuleset.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .ownerId(rs.getLong("owner_id"))
        .identifyingName(rs.getString("identifying_name"))
        .description(rs.getString("description"))
        .category(Category.forField(rs.getString("category")))
        .type(Type.forField(rs.getString("type")))
        .format(TransitDataFormat.forField(rs.getString("format")))
        .build();

    public static final RowMapper<Task> TASK = (rs, rowNum) -> ImmutableTask.builder()
            .id(rs.getLong("id"))
            .entryId(rs.getLong("entry_id"))
            .name(rs.getString("name"))
            .priority(rs.getInt("priority"))
            .created(nullable(rs.getTimestamp("created"), Timestamp::toLocalDateTime))
            .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
            .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
            .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
            .build();

    public static final RowMapper<Package> PACKAGE = (rs, rowNum) -> (Package) ImmutablePackage.builder()
        .id(rs.getLong("id"))
        .taskId(rs.getLong("task_id"))
        .name(rs.getString("name"))
        .path(rs.getString("path"))
        .build();

    public static final Function<String, RowMapper<Organization>> ALIASED_ORGANIZATION = alias ->
        (rs, rowNum) -> ImmutableOrganization.builder()
            .id(rs.getLong(alias + "id"))
            .businessId(rs.getString(alias + "business_id"))
            .name(rs.getString(alias + "name"))
            .build();

    public static final RowMapper<Organization> ORGANIZATION = ALIASED_ORGANIZATION.apply("");

    public static final RowMapper<ImmutableCooperation> COOPERATION = (rs, rowNum) -> ImmutableCooperation.builder()
            .cooperationType(CooperationType.forField(rs.getString("type")))
            .partnerA(ALIASED_ORGANIZATION.apply("partner_a_").mapRow(rs, rowNum))
            .partnerB(ALIASED_ORGANIZATION.apply("partner_b_").mapRow(rs, rowNum))
            .build();

    public static final Function<ObjectMapper, RowMapper<ImmutableEntry>> QUEUE_ENTRY = RowMappers::mapQueueEntry;
    public static final Function<ObjectMapper, RowMapper<ImmutableValidationInput>> VALIDATION_INPUT = RowMappers::mapValidationInput;
    public static final Function<ObjectMapper, RowMapper<ImmutableConversionInput>> CONVERSION_INPUT = RowMappers::mapConversionInput;
    public static final RowMapper<ImmutableError> ERROR = (rs, rowNum) -> ImmutableError.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .taskId(rs.getLong("task_id"))
        .rulesetId(rs.getLong("ruleset_id"))
        .source(rs.getString("source"))
        .message(rs.getString("message"))
        .raw(rs.getBytes("raw"))
        .build();

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

    @SuppressWarnings("unchecked")
    private static RowMapper<ImmutableValidationInput> mapValidationInput(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            String name = rs.getString("name");

            Class<?> cc = findSubtypeFromAnnotation(name);

            return ImmutableValidationInput.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .config(readValue(objectMapper, rs, "config", (Class<RuleConfiguration>) cc))
                    .build();
        };
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<ImmutableConversionInput> mapConversionInput(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            String name = rs.getString("name");

            Class<?> cc = findSubtypeFromAnnotation(name);

            return ImmutableConversionInput.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .config(readValue(objectMapper, rs, "config", (Class<RuleConfiguration>) cc))
                    .build();
        };
    }

    /**
     * Tries to find matching configuration class reference from Jackson's annotations defined in the class based on
     * name of the rule.
     * <p>
     * This method exists to avoid duplicating the type mapping code.
     *
     * @param name Name of the rule
     * @return Matching configuration class reference or null if one couldn't be found.
     */
    private static Class<?> findSubtypeFromAnnotation(String name) {
        JsonSubTypes definedSubTypes = RuleConfiguration.class.getDeclaredAnnotation(JsonSubTypes.class);

        return Streams.filter(definedSubTypes.value(), t -> t.name().equals(name))
            .map(JsonSubTypes.Type::value)
            .findFirst()
            .orElse(null);
    }

    private static <I,O> O nullable(I input, Function<I, O> i2o) {
        return Optional.ofNullable(input).map(i2o).orElse(null);
    }

    private static <O> O readValue(ObjectMapper objectMapper, ResultSet rs, String field, Class<O> type) {
        if (type == null) {
            return null;
        }
        return fromJsonb(rs, field, v -> {
            try {
                return objectMapper.readValue(v, type);
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to read JSONB as valid " + type, e);
            }
            // TODO: This is potentially fatal, we could re-throw instead
            return null;
        });
    }

    private static JsonNode readJson(ObjectMapper objectMapper, ResultSet rs, String field) {
        return fromJsonb(rs, field, v -> {
            try {
                return objectMapper.readTree(v);
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to read JSONB as valid JsonNode", e);
            }
            // TODO: This is potentially fatal, we could re-throw instead
            return null;
        });
    }

    private static <R> R fromJsonb(ResultSet rs, String field, Function<String, R> mapper) {
        try {
            PGobject source = (PGobject) rs.getObject(field);
            if (source != null) {
                return mapper.apply(source.getValue());
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load PGobject", e);
        }
        // TODO: This is potentially fatal, we could re-throw instead
        return null;
    }

    public static <T> PGobject writeJson(ObjectMapper objectMapper, T tree) {
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
