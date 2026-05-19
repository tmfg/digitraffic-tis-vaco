package fi.digitraffic.tis.vaco;

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
import tools.jackson.databind.json.JsonMapper;

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
                    // With empty getter prefix on Immutables, Object methods become spurious properties
                    if ("hashCode".equals(name) || "toString".equals(name)) {
                        return true;
                    }
                    // In Immutables Builder classes, skip addXxx, addAllXxx, and from() methods
                    String className = m.getDeclaringClass().getSimpleName();
                    if ("Builder".equals(className)) {
                        if ("from".equals(name) || name.startsWith("add")) {
                            return true;
                        }
                    }
                    return super.hasIgnoreMarker(config, m);
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
}
