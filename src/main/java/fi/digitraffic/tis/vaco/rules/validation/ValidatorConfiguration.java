package fi.digitraffic.tis.vaco.rules.validation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorRule;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = CanonicalGtfsValidatorRule.RULE_NAME, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = "gtfs.canonical.v4_1_0", value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = EnturNetexValidatorRule.RULE_NAME, value = EnturNetexValidatorConfiguration.class)
})
public interface ValidatorConfiguration {
}
