package fi.digitraffic.tis.vaco.db.model;
import org.immutables.value.Value;

import java.time.LocalDate;

@Value.Immutable
public interface StatisticsRecord {
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
