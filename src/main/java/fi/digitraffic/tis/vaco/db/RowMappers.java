package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ImmutableNotice;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class RowMappers {
    private RowMappers() {}

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
        .dependencies(Set.of(ArraySqlValue.read(rs, "dependencies")))
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

    public static final Function<String, RowMapper<Company>> ALIASED_COMPANY = alias ->
        (rs, rowNum) -> ImmutableCompany.builder()
            .id(rs.getLong(alias + "id"))
            .businessId(rs.getString(alias + "business_id"))
            .name(rs.getString(alias + "name"))
            .contactEmails(List.of(ArraySqlValue.read(rs, alias + "contact_emails")))
            .build();

    public static final RowMapper<Company> COMPANY = ALIASED_COMPANY.apply("");

    public static final RowMapper<ImmutablePartnership> PARTNERSHIP = (rs, rowNum) -> ImmutablePartnership.builder()
            .type(PartnershipType.forField(rs.getString("type")))
            .partnerA(ALIASED_COMPANY.apply("partner_a_").mapRow(rs, rowNum))
            .partnerB(ALIASED_COMPANY.apply("partner_b_").mapRow(rs, rowNum))
            .build();

    public static final Function<ObjectMapper, RowMapper<Entry>> ENTRY = RowMappers::mapQueueEntry;
    public static final Function<ObjectMapper, RowMapper<ImmutableValidationInput>> VALIDATION_INPUT = RowMappers::mapValidationInput;
    public static final Function<ObjectMapper, RowMapper<ImmutableConversionInput>> CONVERSION_INPUT = RowMappers::mapConversionInput;
    public static final Function<ObjectMapper, RowMapper<ImmutableNotice>> UI_NOTICES = RowMappers::mapUiNotices;
    public static final Function<ObjectMapper, RowMapper<ImmutableItemCounter>> UI_NOTICE_COUNTERS = RowMappers::mapUiNoticeCounters;

    public static final RowMapper<ImmutableError> ERROR = (rs, rowNum) -> ImmutableError.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .taskId(rs.getLong("task_id"))
        .rulesetId(rs.getLong("ruleset_id"))
        .source(rs.getString("source"))
        .message(rs.getString("message"))
        .severity(rs.getString("severity"))
        .raw(rs.getBytes("raw"))
        .build();

    private static RowMapper<Entry> mapQueueEntry(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableEntry.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getString("public_id"))
                .businessId(rs.getString("business_id"))
                .name(rs.getString("name"))
                .format(rs.getString("format"))
                .url(rs.getString("url"))
                .etag(rs.getString("etag"))
                .metadata(readJson(objectMapper, rs, "metadata"))
                .created(nullable(rs.getTimestamp("created"), Timestamp::toLocalDateTime))
                .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
                .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
                .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
                .notifications(List.of(ArraySqlValue.read(rs, "notifications")))
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

    @SuppressWarnings("unchecked")
    private static RowMapper<ImmutableNotice> mapUiNotices(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableNotice.builder()
            .code(rs.getString("code"))
            .severity(rs.getString("severity"))
            .total(rs.getInt("total"))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<ImmutableItemCounter> mapUiNoticeCounters(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableItemCounter.builder()
            .name(rs.getString("name"))
            .total(rs.getInt("total"))
            .build();
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
