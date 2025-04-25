package fi.digitraffic.tis.vaco.statistics.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

import java.time.LocalDate;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableStatistics.class)
@JsonDeserialize(as = ImmutableStatistics.class)
public interface Statistics {

    @Value.Parameter
    String status();
    @Value.Parameter
    int count();
    @Value.Parameter
    String unit();
    @Value.Parameter
    String name();
    @Value.Parameter
    LocalDate timestamp();
    @Value.Parameter
    String series();


}
