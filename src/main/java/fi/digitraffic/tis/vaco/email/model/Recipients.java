package fi.digitraffic.tis.vaco.email.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface Recipients {

    @Nullable
    List<String> to();

    @Nullable
    List<String> cc();

    @Nullable
    List<String> bcc();
}
