package fi.digitraffic.tis.vaco.rules.conversion.echo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.conversion.ConverterConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEchoConverterConfiguration.class)
@JsonDeserialize(as = ImmutableEchoConverterConfiguration.class)
public interface EchoConverterConfiguration extends ConverterConfiguration {
    @Nullable
    String message();
}
