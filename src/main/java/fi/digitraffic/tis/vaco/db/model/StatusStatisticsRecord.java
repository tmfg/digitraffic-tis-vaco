package fi.digitraffic.tis.vaco.db.model;
import org.immutables.value.Value;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Value.Immutable
public interface StatusStatisticsRecord {
    @Value.Parameter
    String status();
    @Value.Parameter
    int count();
    @Value.Parameter
    String unit();
    @Value.Parameter
    LocalDate timestamp();

}
