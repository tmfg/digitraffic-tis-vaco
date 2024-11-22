package fi.digitraffic.tis.vaco.credentials.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableHttpBasicAuthenticationDetails.class)
@JsonDeserialize(as = ImmutableHttpBasicAuthenticationDetails.class)
public interface HttpBasicAuthenticationDetails extends AuthenticationDetails {

    @Value.Parameter
    String userId();

    @Value.Parameter
    String password();
}
