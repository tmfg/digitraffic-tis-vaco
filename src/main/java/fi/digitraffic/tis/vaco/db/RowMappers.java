package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.company.model.ImmutableIntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.model.IntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.ConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.FeatureFlagRecord;
import fi.digitraffic.tis.vaco.db.model.FeedRecord;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableContextRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCredentialsRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableEntryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableFeatureFlagRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableFeedRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableFindingRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutablePackageRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutablePartnershipRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableRulesetRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableStatisticsRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableSummaryRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableTaskRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableValidationInputRecord;
import fi.digitraffic.tis.vaco.db.model.PackageRecord;
import fi.digitraffic.tis.vaco.db.model.PartnershipRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.model.StatisticsRecord;
import fi.digitraffic.tis.vaco.db.model.SummaryRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.db.model.ValidationInputRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.ImmutableSubscriptionRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.SubscriptionRecord;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.feeds.model.FeedUri;
import fi.digitraffic.tis.vaco.feeds.model.ImmutableFeedUri;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.summary.model.ImmutableSummary;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.EntryStateService;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyWithFormatSummary;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class RowMappers {

    private RowMappers() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(RowMappers.class);

    public static final RowMapper<RulesetRecord> RULESET_RECORD = (rs, rowNum) -> ImmutableRulesetRecord.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .ownerId(rs.getLong("owner_id"))
        .identifyingName(rs.getString("identifying_name"))
        .description(rs.getString("description"))
        .category(Category.forField(rs.getString("category")))
        .type(RulesetType.forField(rs.getString("type")))
        .format(TransitDataFormat.forField(rs.getString("format")))
        .beforeDependencies(Set.of(ArraySqlValue.read(rs, "before_dependencies")))
        .afterDependencies(Set.of(ArraySqlValue.read(rs, "after_dependencies")))
        .build();

    public static final RowMapper<Task> TASK = (rs, rowNum) -> ImmutableTask.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .name(rs.getString("name"))
        .priority(rs.getInt("priority"))
        .created(readZonedDateTime(rs, "created"))
        .started(readZonedDateTime(rs, "started"))
        .updated(readZonedDateTime(rs, "updated"))
        .completed(readZonedDateTime(rs, "completed"))
        .status(Status.forField(rs.getString("status")))
        .build();

    public static final RowMapper<TaskRecord> TASK_RECORD = (rs, rowNum) -> ImmutableTaskRecord.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .entryId(rs.getLong("entry_id"))
        .name(rs.getString("name"))
        .priority(rs.getInt("priority"))
        .created(readZonedDateTime(rs, "created"))
        .started(readZonedDateTime(rs, "started"))
        .updated(readZonedDateTime(rs, "updated"))
        .completed(readZonedDateTime(rs, "completed"))
        .status(Status.forField(rs.getString("status")))
        .build();

    public static final RowMapper<Status> STATUS = (rs, rowNum) -> Status.forField(rs.getString("status"));

    public static final RowMapper<PackageRecord> PACKAGE_RECORD = (rs, rowNum) -> ImmutablePackageRecord.builder()
        .id(rs.getLong("id"))
        .taskId(rs.getLong("task_id"))
        .name(rs.getString("name"))
        .path(rs.getString("path"))
        .build();

    public static final RowMapper<CompanyRecord> COMPANY_RECORD = (rs, rowNum) -> ImmutableCompanyRecord.builder()
        .id(rs.getLong("id"))
        .businessId(rs.getString("business_id"))
        .name(rs.getString("name"))
        .language(rs.getString("language"))
        .contactEmails(List.of(ArraySqlValue.read(rs, "contact_emails")))
        .adGroupId(rs.getString("ad_group_id"))
        .publish(rs.getBoolean("publish"))
        .codespaces(List.of(ArraySqlValue.read(rs, "codespaces")))
        .notificationWebhookUri(rs.getString("notification_webhook_uri"))
        .website(rs.getString("website"))
        .build();

    public static final RowMapper<PartnershipRecord> PARTNERSHIP_RECORD = (rs, rowNum) -> ImmutablePartnershipRecord.of(
        PartnershipType.forField(rs.getString("type")),
        rs.getLong("partner_a_id"),
        rs.getLong("partner_b_id"));

    public static final RowMapper<ContextRecord> CONTEXT_RECORD = (rs, rowNum) -> ImmutableContextRecord.of(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getString("context"));

    public static final Function<ObjectMapper, RowMapper<EntryRecord>> PERSISTENT_ENTRY = RowMappers::mapEntryEntity;
    public static final Function<ObjectMapper, RowMapper<ValidationInput>> VALIDATION_INPUT = RowMappers::mapValidationInput;
    public static final Function<ObjectMapper, RowMapper<ValidationInputRecord>> VALIDATION_INPUT_RECORD = RowMappers::mapValidationInputRecord;
    public static final Function<ObjectMapper, RowMapper<ConversionInput>> CONVERSION_INPUT = RowMappers::mapConversionInput;
    public static final Function<ObjectMapper, RowMapper<ConversionInputRecord>> CONVERSION_INPUT_RECORD = RowMappers::mapConversionInputRecord;
    public static final Function<ObjectMapper, RowMapper<FeedRecord>> FEED = RowMappers::mapFeedRecord;

    public static final RowMapper<FindingRecord> FINDING_RECORD = (rs, rowNum) -> ImmutableFindingRecord.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .taskId(rs.getLong("task_id"))
        .rulesetId(rs.getLong("ruleset_id"))
        .source(rs.getString("source"))
        .message(rs.getString("message"))
        .severity(rs.getString("severity"))
        .raw(rs.getBytes("raw"))
        .build();

    public static final RowMapper<Summary> SUMMARY = (rs, rowNum) -> ImmutableSummary.builder()
        .id(rs.getLong("id"))
        .taskId(rs.getLong("task_id"))
        .name(rs.getString("name"))
        .rendererType(RendererType.forField(rs.getString("renderer_type")))
        .raw(rs.getBytes("raw"))
        .build();

    public static final RowMapper<SummaryRecord> SUMMARY_RECORD = (rs, rowNum) -> ImmutableSummaryRecord.builder()
        .id(rs.getLong("id"))
        .taskId(rs.getLong("task_id"))
        .name(rs.getString("name"))
        .raw(rs.getBytes("raw"))
        .created(readZonedDateTime(rs, "created"))
        .rendererType(RendererType.forField(rs.getString("renderer_type")))
        .build();

    public static final Function<ObjectMapper, RowMapper<Summary>> SUMMARY_WITH_CONTENT =
        RowMappers::mapSummaryWithContent;

    private static RowMapper<Summary> mapSummaryWithContent(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            Object content = null;
            switch (rs.getString("name")) {
                case "agencies" -> {
                    try {
                        List<Map<String, String>> agencies = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                        content = EntryStateService.getAgencyCardUiContent(agencies);
                    } catch (IOException e) {
                        LOGGER.error("Failed to transform {} bytes into summary content ", rs.getString("raw"), e);
                    }
                }
                // XXX: Only the first line of feedinfo is persisted, if any. This is a bug in UI which is hardcoded
                //      too deeply to rely on this and would be too much effort to refactor at the moment.
                case "feedInfo" -> {
                    try {
                        Map<String, String> feedInfo = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                        content = EntryStateService.getFeedInfoUiContent(feedInfo);
                    } catch (IOException e) {
                        LOGGER.error("Failed to transform {} bytes into summary content ", rs.getString("raw"), e);
                    }
                }
                default -> {
                    try {
                        content = objectMapper.readValue(rs.getBytes("raw"), new TypeReference<>() {});
                    } catch (IOException e) {
                        LOGGER.error("Failed to transform {} bytes into summary content ", rs.getString("raw"), e);
                    }
                }
            }
            return ImmutableSummary.builder()
                .id(rs.getLong("id"))
                .taskId(rs.getLong("task_id"))
                .name(rs.getString("name"))
                .rendererType(RendererType.forField(rs.getString("renderer_type")))
                .content(content)
                .build();
        };
    }

    public static final RowMapper<FeatureFlagRecord> FEATURE_FLAG_RECORD = (rs, rowNum) -> ImmutableFeatureFlagRecord.of(
            rs.getLong("id"),
            rs.getString("name"),
            readZonedDateTime(rs, "modified")
        ).withModifiedBy(rs.getString("modified_by"))
        .withEnabled(rs.getBoolean("enabled"));

    public static final RowMapper<IntermediateHierarchyLink> INTERMEDIATE_HIERARCHY_LINK = (rs, rowNum) -> ImmutableIntermediateHierarchyLink.builder()
        .parentId(nullableLong(rs, "parent_id"))
        .childId(nullableLong(rs, "child_id"))
        .build();

    private static Long nullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long possibleValue = rs.getLong(columnLabel);
        if (rs.wasNull()) {
            return null;
        } else {
            return possibleValue;
        }
    }

    private static RowMapper<EntryRecord> mapEntryEntity(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableEntryRecord.builder()
            .id(rs.getLong("id"))
            .publicId(rs.getString("public_id"))
            .businessId(rs.getString("business_id"))
            .name(rs.getString("name"))
            .format(rs.getString("format"))
            .url(rs.getString("url"))
            .etag(rs.getString("etag"))
            .metadata(readJson(objectMapper, rs, "metadata"))
            .created(readZonedDateTime(rs, "created"))
            .started(readZonedDateTime(rs, "started"))
            .updated(readZonedDateTime(rs, "updated"))
            .completed(readZonedDateTime(rs, "completed"))
            .notifications(List.of(ArraySqlValue.read(rs, "notifications")))
            .status(Status.forField(rs.getString("status")))
            .context(nullableLong(rs, "context_id"))
            .credentials(nullableLong(rs, "credentials_id"))
            .sendNotifications(rs.getBoolean("send_notifications"))
            .build();
    }

    public static final RowMapper<CompanyLatestEntry> DATA_DELIVERY = (rs, rowNum) -> ImmutableCompanyLatestEntry.builder()
        .companyName(rs.getString("company_name"))
        .businessId(rs.getString("business_id"))
        .publicId(rs.getString("public_id"))
        .format(rs.getString("input_format"))
        .url(rs.getString("source_url"))
        .feedName(rs.getString("name"))
        .convertedFormat(rs.getString("converted_format"))
        .created(readZonedDateTime(rs, "created"))
        .status(rs.getString("status") != null ? Status.forField(rs.getString("status")) : null)
        .context(rs.getString("context"))
        .build();

    public static final RowMapper<CompanyWithFormatSummary> COMPANY_WITH_FORMATS = (rs, rowNum) -> ImmutableCompanyWithFormatSummary.builder()
        .businessId(rs.getString("business_id"))
        .name(rs.getString("name"))
        .formatSummary(rs.getString("format_summary"))
        .build();

    public static final RowMapper<SubscriptionRecord> SUBSCRIPTION_RECORD = (rs, rowNum) -> ImmutableSubscriptionRecord.builder()
        .id(rs.getLong("id"))
        .publicId(rs.getString("public_id"))
        .type(SubscriptionType.forField(rs.getString("type")))
        .subscriberId(rs.getLong("subscriber_id"))
        .resourceId(rs.getLong("resource_id"))
        .build();

    @SuppressWarnings("unchecked")
    private static RowMapper<ValidationInput> mapValidationInput(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            String name = rs.getString("name");

            Class<?> cc = findSubtypeFromAnnotation(name);

            return ImmutableValidationInput.builder()
                    .name(rs.getString("name"))
                    .config(readValue(objectMapper, rs, "config", (Class<RuleConfiguration>) cc))
                    .build();
        };
    }

    private static RowMapper<ValidationInputRecord> mapValidationInputRecord(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableValidationInputRecord.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .config(readJson(objectMapper, rs, "config"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<ConversionInput> mapConversionInput(ObjectMapper objectMapper) {
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

    private static RowMapper<ConversionInputRecord> mapConversionInputRecord(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableConversionInputRecord.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .config(readJson(objectMapper, rs, "config"))
                .build();
    }

    public static RowMapper<FeedRecord> mapFeedRecord(ObjectMapper objectMapper) {
        return (rs, rowNum) -> {
            try {
                return ImmutableFeedRecord.builder()
                    .id(rs.getLong("id"))
                    .publicId(rs.getString("public_id"))
                    .ownerId(rs.getLong("owner_id"))
                    .format(TransitDataFormat.forField(rs.getString("format")))
                    .uri(mapFeedUrl(rs, objectMapper))
                    .processingEnabled(rs.getBoolean("processing_enabled"))
                    .build();
            } catch (JsonProcessingException e) {
                throw new InvalidMappingException("Cannot deserialize data contained in database for record", e);
            }
        };
    }

    private static FeedUri mapFeedUrl(ResultSet rs, ObjectMapper objectMapper) throws SQLException, JsonProcessingException {
            return ImmutableFeedUri.builder()
                .uri(rs.getString("uri"))
                .queryParams(objectMapper.readValue(rs.getString("query_params"), new TypeReference<Map<String, String>>() {}))
                .httpMethod(rs.getString("http_method"))
                .requestBody(rs.getString("request_body"))
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
                LOGGER.error("Failed to read JSONB as valid {}", type, e);
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

    private static ZonedDateTime readZonedDateTime(ResultSet rs, String timestampFieldName) throws SQLException {
        return nullable(rs.getTimestamp(timestampFieldName), ts -> ts.toInstant().atZone(ZoneId.of("UTC").normalized()));
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

    public static PGInterval writeInterval(Duration duration) {
        PGInterval pgi = new PGInterval();
        // Java Duration is modeled as seconds and nanoseconds, so it doesn't have any higher level understanding of
        // time than days.
        pgi.setDays((int) duration.toDaysPart());
        pgi.setHours(duration.toHoursPart());
        pgi.setMinutes(duration.toMinutesPart());
        pgi.setSeconds(duration.toSecondsPart());
        return pgi;
    }

    public static RowMapper<CredentialsRecord> CREDENTIALS_RECORD(Function<byte[], byte[]> converter) {
        return (rs, rowNum) -> {
            return ImmutableCredentialsRecord.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getString("public_id"))
                .ownerId(rs.getLong("owner_id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .type(CredentialsType.forField(rs.getString("type")))
                .details(converter.apply(rs.getBytes("details")))
                .urlPattern(rs.getString("url_pattern"))
                .build();
        };
    }

    public static LocalDate readLocalDate(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, LocalDate.class);
    }

    public static RowMapper<StatisticsRecord> STATISTICS_RECORD() {
        return (rs, rowNum) -> ImmutableStatisticsRecord.builder()
            .name(rs.getString("name"))
            .status(rs.getString("subserie"))
            .count(rs.getInt("count"))
            .unit(rs.getString("unit"))
            .timestamp(readLocalDate(rs,"record_created_at"))
            .series(rs.getString("series"))
            .build();
    }

}
