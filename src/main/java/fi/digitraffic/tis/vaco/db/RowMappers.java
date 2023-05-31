package fi.digitraffic.tis.vaco.db;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.CooperationType;
import fi.digitraffic.tis.vaco.validation.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.validation.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRuleSet;
import org.springframework.jdbc.core.RowMapper;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;

public class RowMappers {
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

    private static <I,O> O nullable(I input, Function<I, O> i2o) throws SQLException {
        return Optional.ofNullable(input).map(i2o).orElse(null);
    }
}
