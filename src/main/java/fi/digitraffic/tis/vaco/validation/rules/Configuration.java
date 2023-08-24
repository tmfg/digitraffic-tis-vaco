package fi.digitraffic.tis.vaco.validation.rules;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.validation.rules.netex.EnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.validation.rules.netex.EnturNetexValidatorRule;

/**
 * Marker interface for validation rule configurations. Used mainly to instruct Jackson how to properly (de)serialize
 * related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = CanonicalGtfsValidatorRule.RULE_NAME, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = EnturNetexValidatorRule.RULE_NAME, value = EnturNetexValidatorConfiguration.class)
})
public interface Configuration {
}
