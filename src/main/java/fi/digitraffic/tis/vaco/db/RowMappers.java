package fi.digitraffic.tis.vaco.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.CooperationType;
import fi.digitraffic.tis.vaco.validation.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.validation.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRuleSet;
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

    public static final RowMapper<ImmutableRuleSet> RULESET = (rs, rowNum) -> ImmutableRuleSet.builder()
            .id(rs.getLong("id"))
            .publicId(rs.getString("public_id"))
            .ownerId(rs.getLong("owner_id"))
            .identifyingName(rs.getString("identifying_name"))
            .description(rs.getString("description"))
            .category(Category.forField(rs.getString("category")))
            .build();

    public static final RowMapper<ImmutablePhase> PHASE = (rs, rowNum) -> ImmutablePhase.builder()
            .id(rs.getLong("id"))
            .entryId(rs.getLong("entry_id"))
            .name(rs.getString("name"))
            .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
            .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
            .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
            .build();

    public static final RowMapper<ImmutableOrganization> ORGANIZATION = (rs, rowNum) -> ImmutableOrganization.builder()
            .id(rs.getLong("id"))
            .publicId(rs.getString("public_id"))
            .businessId(rs.getString("business_id"))
            .name(rs.getString("name"))
            .build();

    public static final RowMapper<ImmutableCooperation> COOPERATION = (rs, rowNum) -> ImmutableCooperation.builder()
            .cooperationType(CooperationType.forField(rs.getString("type")))
            .partnerA(rs.getLong("partner_a_id"))
            .partnerB(rs.getLong("partner_b_id"))
            .build();
    public static final Function<ObjectMapper, RowMapper<ImmutableQueueEntry>> QUEUE_ENTRY = RowMappers::mapRow;

    private static <I,O> O nullable(I input, Function<I, O> i2o) throws SQLException {
        return Optional.ofNullable(input).map(i2o).orElse(null);
    }

    private static RowMapper<ImmutableQueueEntry> mapRow(ObjectMapper objectMapper) {
        return (rs, rowNum) -> ImmutableQueueEntry.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getString("public_id"))
                .businessId(rs.getString("business_id"))
                .format(rs.getString("format"))
                .url(rs.getString("url"))
                .etag(rs.getString("etag"))
                .metadata(readJson(objectMapper, rs, "metadata"))
                .build();
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

}
