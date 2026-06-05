package fi.digitraffic.tis.vaco.api.model.credentials;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableUpdateCredentialsRequest.class)
@JsonDeserialize(builder = ImmutableUpdateCredentialsRequest.Builder.class)
public interface UpdateCredentialsRequest {

    @Value.Default
    default CredentialsType type() {
        return CredentialsType.HTTP_BASIC;
    }

    @Value.Parameter
    String name();

    @Value.Parameter
    String description();

    @Nullable
    AuthenticationDetails details();

    @Nullable
    String urlPattern();
}
