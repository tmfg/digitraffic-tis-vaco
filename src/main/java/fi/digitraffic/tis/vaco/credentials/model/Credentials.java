package fi.digitraffic.tis.vaco.credentials.model;

import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.company.model.Company;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCredentials.class)
@JsonDeserialize(as = ImmutableCredentials.class)
public interface Credentials {
    /**
     * Temporary identifier to use when mapping between types in contexts where the public id is not available otherwise.
     * Use with extreme caution and preferably not at all unless you really, really, REALLY have to!
     */
    String NON_PERSISTED_PUBLIC_ID = "<< !!! NON-PERSISTED CREDENTIALS !!! >>";

    CredentialsType type();

    String publicId();

    String name();

    @Nullable
    String description();

    Company owner();

    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    AuthenticationDetails details();

    @Nullable
    String urlPattern();
}
