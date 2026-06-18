package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.gbfs.ImmutableEnturGbfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs.ImmutableCanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs2netex.ImmutableFintrafficGtfs2NetexConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.ImmutableEnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.ImmutableEnturNetex2GtfsConverterConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.jsontype.NamedType;

@Configuration
public class JacksonFeaturesConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictCoercionConfiguration() {
        return builder -> builder
            // @JsonView made easy; disabled by default in Jackson 3
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(
                // use nulls for unknown implementations to allow for relaxed matching of requested vs resolved
                // RuleConfigurations
                DeserializationFeature.FAIL_ON_INVALID_SUBTYPE,
                // JsonView may hide polymorphic data; disabling this will prevent exception to be thrown when trying
                // to deserialize such
                DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)
            // prevent coercion of anything to strings to allow Jakarta Validation to work as intended
            .withCoercionConfigDefaults(cfg -> {
                cfg.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Array, CoercionAction.Fail);
                cfg.setCoercion(CoercionInputShape.Object, CoercionAction.Fail);
            });
    }

    /**
     * Register the Immutable implementations of {@link fi.digitraffic.tis.vaco.rules.RuleConfiguration} with their
     * rule name subtypes. Jackson 3 does not walk the type hierarchy when resolving the polymorphic {@code @type} id
     * for serialization, so without this registration it falls back to the Immutable class simple name (e.g.
     * {@code "ImmutableEnturNetexValidatorConfiguration"}) instead of the registered rule name (e.g.
     * {@code "netex.entur"}), causing the SQS roundtrip to silently lose the configuration.
     */
    @Bean
    public JsonMapperBuilderCustomizer ruleConfigurationSubtypeRegistration() {
        return builder -> builder.registerSubtypes(
            new NamedType(ImmutableCanonicalGtfsValidatorConfiguration.class, RuleName.GTFS_CANONICAL),
            new NamedType(ImmutableEnturNetexValidatorConfiguration.class, RuleName.NETEX_ENTUR),
            new NamedType(ImmutableEnturNetex2GtfsConverterConfiguration.class, RuleName.NETEX2GTFS_ENTUR),
            new NamedType(ImmutableFintrafficGtfs2NetexConverterConfiguration.class, RuleName.GTFS2NETEX_FINTRAFFIC),
            new NamedType(ImmutableEnturGbfsValidatorConfiguration.class, RuleName.GBFS_ENTUR)
        );
    }
}
