package fi.digitraffic.tis.vaco.conversion.rules.echo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.conversion.rules.Configuration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEchoConversionConfiguration.class)
@JsonDeserialize(as = ImmutableEchoConversionConfiguration.class)
public interface EchoConversionConfiguration extends Configuration {
    @Nullable
    String message();
}
