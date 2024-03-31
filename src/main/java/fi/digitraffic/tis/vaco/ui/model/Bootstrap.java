package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableBootstrap.class)
@JsonDeserialize(as = ImmutableBootstrap.class)
public interface Bootstrap {
    @Value.Parameter
    String environment();

    @Value.Parameter
    String baseUrl();

    @Value.Parameter
    String tenantId();

    @Value.Parameter
    String clientId();

    @Value.Parameter
    String buildInfo();
}
