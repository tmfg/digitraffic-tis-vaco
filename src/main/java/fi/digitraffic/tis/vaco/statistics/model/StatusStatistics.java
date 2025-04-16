package fi.digitraffic.tis.vaco.statistics.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

import java.time.LocalDate;

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
    LocalDate timestamp();


}
