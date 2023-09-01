package fi.digitraffic.tis.vaco.rules.conversion;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.rules.conversion.echo.EchoConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.conversion.echo.EchoConverterRule;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = EchoConverterRule.RULE_NAME, value = EchoConverterConfiguration.class)
})
public interface ConverterConfiguration {
}
