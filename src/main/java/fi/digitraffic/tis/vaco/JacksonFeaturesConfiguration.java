package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableHttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.gbfs.ImmutableEnturGbfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs.ImmutableCanonicalGtfsValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.gtfs2netex.ImmutableFintrafficGtfs2NetexConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.ImmutableEnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.ImmutableEnturNetex2GtfsConverterConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AccessorNamingStrategy;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

@Configuration
public class JacksonFeaturesConfiguration {

    private static final DefaultAccessorNamingStrategy.Provider STANDARD = new DefaultAccessorNamingStrategy.Provider();
    private static final DefaultAccessorNamingStrategy.Provider IMMUTABLES_POJO = new DefaultAccessorNamingStrategy.Provider()
        .withGetterPrefix("")
        .withIsGetterPrefix("");
    private static final DefaultAccessorNamingStrategy.Provider IMMUTABLES_BUILDER = new DefaultAccessorNamingStrategy.Provider()
        .withBuilderPrefix("");

    /**
     * Applies Immutables-compatible accessor naming and annotation introspection to the given mapper builder.
     * Immutables-generated classes (name starts with "Immutable") use no-prefix accessors ({@code businessId()}
     * instead of {@code getBusinessId()}) and no-prefix builder setters (instead of {@code withBusinessId()}).
     * Call this on any locally-created mapper that needs to handle Immutables-generated types.
     */
    public static JsonMapper.Builder configureForImmutables(JsonMapper.Builder builder) {
        return builder
            // Fields without any @JsonView annotation must be included when writing with a view.
            // Jackson 3 defaults this to false; set it to true so Immutables-generated classes
            // (whose fields have no @JsonView) are serialized correctly when a view is active.
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .accessorNaming(new DefaultAccessorNamingStrategy.Provider() {
                @Override
                public AccessorNamingStrategy forPOJO(MapperConfig<?> config, AnnotatedClass targetClass) {
                    if (targetClass.getRawType().getSimpleName().startsWith("Immutable")) {
                        // Immutables classes may inherit Java-style getters (getXxx) from non-Immutables
                        // interfaces alongside their own no-prefix accessors (businessId()). Use standard
                        // naming first; fall back to no-prefix for bare Immutables accessors.
                        AccessorNamingStrategy std = STANDARD.forPOJO(config, targetClass);
                        AccessorNamingStrategy imm = IMMUTABLES_POJO.forPOJO(config, targetClass);
                        return new AccessorNamingStrategy() {
                            @Override
                            public String findNameForRegularGetter(AnnotatedMethod am, String name) {
                                String result = std.findNameForRegularGetter(am, name);
                                return result != null ? result : imm.findNameForRegularGetter(am, name);
                            }
                            @Override
                            public String findNameForIsGetter(AnnotatedMethod am, String name) {
                                String result = std.findNameForIsGetter(am, name);
                                return result != null ? result : imm.findNameForIsGetter(am, name);
                            }
                            @Override
                            public String findNameForMutator(AnnotatedMethod am, String name) {
                                return imm.findNameForMutator(am, name);
                            }
                            @Override
                            public String modifyFieldName(AnnotatedField field, String name) {
                                return std.modifyFieldName(field, name);
                            }
                        };
                    }
                    return STANDARD.forPOJO(config, targetClass);
                }

                @Override
                public AccessorNamingStrategy forBuilder(MapperConfig<?> config,
                        AnnotatedClass builderClass, BeanDescription valueTypeDesc) {
                    Class<?> enclosing = builderClass.getRawType().getDeclaringClass();
                    if (enclosing != null && enclosing.getSimpleName().startsWith("Immutable")) {
                        return IMMUTABLES_BUILDER.forBuilder(config, builderClass, valueTypeDesc);
                    }
                    return STANDARD.forBuilder(config, builderClass, valueTypeDesc);
                }

                @Override
                public AccessorNamingStrategy forRecord(MapperConfig<?> config, AnnotatedClass recordClass) {
                    return STANDARD.forRecord(config, recordClass);
                }
            })
            .annotationIntrospector(new JacksonAnnotationIntrospector() {
                @Override
                public boolean hasIgnoreMarker(MapperConfig<?> config, AnnotatedMember m) {
                    String name = m.getName();
                    // With empty getter prefix on Immutables, Object methods become spurious properties.
                    // Guard to Immutables-generated types to avoid suppressing legitimate properties on other types.
                    String className = m.getDeclaringClass().getSimpleName();
                    if (className.startsWith("Immutable")) {
                        if ("hashCode".equals(name) || "toString".equals(name)) {
                            return true;
                        }
                    }
                    return super.hasIgnoreMarker(config, m);
                }

                /**
                 * Jackson 3 no longer propagates {@code @JsonNaming} from the value type interface to the
                 * Immutables-generated builder class. This override restores that behaviour: when the annotated
                 * class is an Immutables builder, check the enclosing value class's interfaces for
                 * {@code @JsonNaming} and return the strategy found there, if any.
                 */
                @Override
                public Object findNamingStrategy(MapperConfig<?> config, AnnotatedClass ac) {
                    Class<?> declaring = ac.getRawType().getDeclaringClass();
                    if (declaring != null && declaring.getSimpleName().startsWith("Immutable")) {
                        for (Class<?> iface : declaring.getInterfaces()) {
                            JsonNaming naming = iface.getAnnotation(JsonNaming.class);
                            if (naming != null) {
                                return naming.value();
                            }
                        }
                    }
                    return super.findNamingStrategy(config, ac);
                }
            });
    }

    /**
     * Configure Jackson 3 for Immutables-generated classes via Spring Boot's mapper customization hook.
     */
    @Bean
    public JsonMapperBuilderCustomizer immutablesBuilderConfiguration() {
        return builder -> configureForImmutables(builder);
    }

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
            new NamedType(ImmutableEnturGbfsValidatorConfiguration.class, RuleName.GBFS_ENTUR),
            new NamedType(ImmutableHttpBasicAuthenticationDetails.class, CredentialsType.Name.HTTP_BASIC)
        );
    }
}
