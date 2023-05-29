package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import org.springframework.jdbc.core.RowMapper;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;

public class RowMappers {
    public static RowMapper<ImmutablePhase> PHASE = (rs, rowNum) -> ImmutablePhase.builder()
                .id(rs.getLong("id"))
                .entryId(rs.getLong("entry_id"))
                .name(rs.getString("name"))
                .started(nullable(rs.getTimestamp("started"), Timestamp::toLocalDateTime))
                .updated(nullable(rs.getTimestamp("updated"), Timestamp::toLocalDateTime))
                .completed(nullable(rs.getTimestamp("completed"), Timestamp::toLocalDateTime))
                .build();

    private static <I,O> O nullable(I input, Function<I, O> i2o) throws SQLException {
        return Optional.ofNullable(input).map(i2o).orElse(null);
    }
}
