package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface CredentialsRecord {

    long id();

    CredentialsType type();

    String publicId();

    String name();

    @Nullable
    String description();

    long ownerId();

    byte[] details();

}
