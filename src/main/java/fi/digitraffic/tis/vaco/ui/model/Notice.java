package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.findings.Finding;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableNotice.class)
@JsonDeserialize(as = ImmutableNotice.class)
public interface Notice {

    String code();

    String severity();

    int total();

    // description

    @Nullable
    List<Finding> findings();
}
