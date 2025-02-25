package fi.digitraffic.tis.vaco.api.model.credentials;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableUpdateCredentialsRequest.class)
@JsonDeserialize(as = ImmutableUpdateCredentialsRequest.class)
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
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    AuthenticationDetails details();

    @Nullable
    String urlPattern();
}
