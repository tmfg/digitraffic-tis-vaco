package fi.digitraffic.tis.vaco.statistics.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableSubscription;
import org.immutables.value.Value;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableStatusStatistics.class)
@JsonDeserialize(as = ImmutableStatusStatistics.class)
public interface StatusStatistics {

    @Value.Parameter
    String status();
    @Value.Parameter
    int count();
    @Value.Parameter
    String unit();
    @Value.Parameter
    ZonedDateTime timestamp();


}
