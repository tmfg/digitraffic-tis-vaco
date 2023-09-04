package fi.digitraffic.tis.vaco.conversion.rules;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.conversion.rules.echo.EchoConversionConfiguration;
import fi.digitraffic.tis.vaco.conversion.rules.echo.EchoConversionRule;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = EchoConversionRule.RULE_NAME, value = EchoConversionConfiguration.class)
})
public interface Configuration {
}
