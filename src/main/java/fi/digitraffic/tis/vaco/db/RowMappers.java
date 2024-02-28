package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableIntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.IntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import fi.digitraffic.tis.vaco.featureflags.model.ImmutableFeatureFlag;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.findings.ImmutableFinding;
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
import fi.digitraffic.tis.vaco.summary.model.ImmutableSummary;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Agency;
import fi.digitraffic.tis.vaco.summary.model.gtfs.FeedInfo;
import fi.digitraffic.tis.vaco.ui.EntryStateService;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import fi.digitraffic.tis.vaco.ui.model.ImmutableAggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyWithFormatSummary;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
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
            .status(Status.forField(rs.getString("status")))
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
            .language(rs.getString(alias + "language"))
            .contactEmails(List.of(ArraySqlValue.read(rs, alias + "contact_emails")))
            .adGroupId(rs.getString(alias + "ad_group_id"))
            .build();

    public static final RowMapper<Company> COMPANY = ALIASED_COMPANY.apply("");

    public static final RowMapper<Partnership> PARTNERSHIP = (rs, rowNum) -> ImmutablePartnership.builder()
            .type(PartnershipType.forField(rs.getString("type")))
            .partnerA(ALIASED_COMPANY.apply("partner_a_").mapRow(rs, rowNum))
            .partnerB(ALIASED_COMPANY.apply("partner_b_").mapRow(rs, rowNum))
            .build();

    public static final Function<ObjectMapper, RowMapper<Entry>> ENTRY = RowMappers::mapQueueEntry;
    public static final Function<ObjectMapper, RowMapper<ImmutableValidationInput>> VALIDATION_INPUT = RowMappers::mapValidationInput;
    public static final Function<ObjectMapper, RowMapper<ImmutableConversionInput>> CONVERSION_INPUT = RowMappers::mapConversionInput;
    public static final Function<ObjectMapper, RowMapper<AggregatedFinding>> UI_AGGREGATED_FINDINGS = RowMappers::mapUiAggregatedFinding;
    public static final Function<ObjectMapper, RowMapper<ItemCounter>> UI_FINDING_COUNTERS = RowMappers::mapUiFindingCounters;

    public static final RowMapper<Finding> FINDING = (rs, rowNum) -> ImmutableFinding.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .taskId(rs.getLong("task_id"))
        .rulesetId(rs.getLong("ruleset_id"))
        .source(rs.getString("source"))
        .message(rs.getString("message"))
        .severity(rs.getString("severity"))
        .raw(rs.getBytes("raw"))
        .build();

    public static final RowMapper<GroupIdMappingTask> ADMIN_GROUPID = (rs, rowNum) -> ImmutableGroupIdMappingTask.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .groupId(rs.getString("group_id"))
        .skip(rs.getBoolean("skip"))
        .created(nullable(rs.getTimestamp("created"), Timestamp::toLocalDateTime))
        .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
        .completedBy(rs.getString("completed_by"))
        .build();

    public static final RowMapper<Summary> SUMMARY = (rs, rowNum) -> ImmutableSummary.builder()
        .id(rs.getLong("id"))
        .taskId(rs.getLong("task_id"))
        .name(rs.getString("name"))
        .rendererType(RendererType.forField(rs.getString("renderer_type")))
        .raw(rs.getBytes("raw"))
        .build();

    public static final Function<ObjectMapper, RowMapper<Summary>> SUMMARY_WITH_CONTENT =
        RowMappers::mapSummaryWithContent;

    private static RowMapper<Summary> mapSummaryWithContent(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            try {
                Object content;
                switch (rs.getString("name")) {
                    case "agencies" -> {
                        List<Agency> agencies = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                        content = EntryStateService.getAgencyCardUiContent(agencies);
                    }
                    case "feedInfo" -> {
                        FeedInfo feedInfo = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                        content = EntryStateService.getFeedInfoUiContent(feedInfo);
                    }
                    default -> content = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                }
                return ImmutableSummary.builder()
                    .id(rs.getLong("id"))
                    .taskId(rs.getLong("task_id"))
                    .name(rs.getString("name"))
                    .rendererType(RendererType.forField(rs.getString("renderer_type")))
                    .content(content)
                    .build();
            } catch (IOException e) {
                LOGGER.error("Failed to transform {} bytes into summary content ", rs.getString("name"), e);
            }

            return null;
        };
    }

    public static final RowMapper<FeatureFlag> FEATURE_FLAG = (rs, rowNum) -> ImmutableFeatureFlag.builder()
        .id(rs.getLong("id"))
        .modified(nullable(rs.getTimestamp("modified"), Timestamp::toLocalDateTime))
        .modifiedBy(rs.getString("modified_by"))
        .name(rs.getString("name"))
        .enabled(rs.getBoolean("enabled"))
        .build();

    public static RowMapper<IntermediateHierarchyLink> INTERMEDIATE_HIERARCHY_LINK = (rs, rowNum) -> ImmutableIntermediateHierarchyLink.builder()
        .parentId(nullableLong(rs, "parent_id"))
        .childId(nullableLong(rs, "child_id"))
        .company(COMPANY.mapRow(rs, rowNum))
        .build();

    private static Long nullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long possibleValue = rs.getLong(columnLabel);
        if (rs.wasNull()) {
            return null;
        } else {
            return possibleValue;
        }
    }

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
            .status(Status.forField(rs.getString("status")))
            .build();
    }

    public static final RowMapper<CompanyLatestEntry> COMPANY_LATEST_ENTRY = (rs, rowNum) -> ImmutableCompanyLatestEntry.builder()
        .companyName(rs.getString("company_name"))
        .businessId(rs.getString("business_id"))
        .publicId(rs.getString("public_id"))
        .format(rs.getString("format"))
        .convertedFormat(rs.getString("converted_format"))
        .created(nullable(rs.getTimestamp("created"), Timestamp::toLocalDateTime))
        .status(rs.getString("status") != null ? Status.forField(rs.getString("status")) : null)
        .build();

    public static final RowMapper<CompanyWithFormatSummary> COMPANY_WITH_FORMATS = (rs, rowNum) -> ImmutableCompanyWithFormatSummary.builder()
        .businessId(rs.getString("business_id"))
        .name(rs.getString("name"))
        .formatSummary(rs.getString("format_summary"))
        .build();

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
    private static RowMapper<AggregatedFinding> mapUiAggregatedFinding(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableAggregatedFinding.builder()
            .code(rs.getString("code"))
            .severity(rs.getString("severity"))
            .total(rs.getInt("total"))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<ItemCounter> mapUiFindingCounters(ObjectMapper objectMapper) {
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
