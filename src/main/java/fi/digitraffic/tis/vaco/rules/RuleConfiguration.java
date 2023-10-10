package fi.digitraffic.tis.vaco.rules;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.rules.conversion.echo.EchoConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.conversion.echo.EchoConverterRule;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorConfiguration;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = EchoConverterRule.RULE_NAME, value = EchoConverterConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GTFS_CANONICAL_4_0_0, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GTFS_CANONICAL_4_1_0, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.NETEX_ENTUR_1_0_1, value = EnturNetexValidatorConfiguration.class)
})
public interface RuleConfiguration {
}
