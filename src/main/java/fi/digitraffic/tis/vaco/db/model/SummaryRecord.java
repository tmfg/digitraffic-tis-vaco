package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.summary.model.RendererType;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
public interface SummaryRecord {
    Long id();

    Long taskId();

    String name();

    byte[] raw();

    ZonedDateTime created();

    @Value.Default
    default RendererType rendererType() {
        return RendererType.UNKNOWN;
    }
}
