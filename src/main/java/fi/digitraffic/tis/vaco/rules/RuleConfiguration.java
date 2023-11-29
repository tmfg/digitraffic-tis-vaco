package fi.digitraffic.tis.vaco.rules;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import fi.digitraffic.tis.vaco.rules.model.gtfs.CanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs2netex.FintrafficGtfs2NetexConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.EnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.EnturNetex2GtfsConverterConfiguration;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(name = RuleName.GTFS_CANONICAL_4_0_0, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GTFS_CANONICAL_4_1_0, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.NETEX_ENTUR_1_0_1, value = EnturNetexValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.NETEX2GTFS_ENTUR_2_0_6, value = EnturNetex2GtfsConverterConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0, value = FintrafficGtfs2NetexConverterConfiguration.class)
})
public interface RuleConfiguration {
}
