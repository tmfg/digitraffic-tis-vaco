package fi.digitraffic.tis.vaco.rules;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fi.digitraffic.tis.vaco.rules.gbfs.EnturGbfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs.CanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs2netex.FintrafficGtfs2NetexConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.EnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.EnturNetex2GtfsConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.ssim.FintrafficSsimValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.ssim2netex.FintrafficSsim2NetexConverterConfiguration;

/**
 * Marker interface for validation and conversion rule configurations. Used mainly to instruct Jackson how to properly
 * (de)serialize related types.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(name = RuleName.GTFS_CANONICAL, value = CanonicalGtfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.NETEX_ENTUR, value = EnturNetexValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.NETEX2GTFS_ENTUR, value = EnturNetex2GtfsConverterConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GTFS2NETEX_FINTRAFFIC, value = FintrafficGtfs2NetexConverterConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.GBFS_ENTUR, value = EnturGbfsValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.SSIM_FINTRAFFIC, value = FintrafficSsimValidatorConfiguration.class),
    @JsonSubTypes.Type(name = RuleName.SSIM2NETEX_FINTRAFFIC, value = FintrafficSsim2NetexConverterConfiguration.class)
})
public interface RuleConfiguration {
}
