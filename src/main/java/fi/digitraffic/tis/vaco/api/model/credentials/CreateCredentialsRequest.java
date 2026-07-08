package fi.digitraffic.tis.vaco.api.model.credentials;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateCredentialsRequest.class)
@JsonDeserialize(as = ImmutableCreateCredentialsRequest.class)
public interface CreateCredentialsRequest {

    @Value.Parameter
    CredentialsType type();

    @Value.Parameter
    String name();

    @Value.Parameter
    String description();

    @Value.Parameter
    String owner();

    @Value.Parameter
    AuthenticationDetails details();

    @Nullable
    String urlPattern();
}
