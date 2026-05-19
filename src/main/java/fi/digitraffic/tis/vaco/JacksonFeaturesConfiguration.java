package fi.digitraffic.tis.vaco;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

@Configuration
public class JacksonFeaturesConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer immutablesBuilderConfiguration() {
        return builder -> builder.annotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public JsonPOJOBuilder.Value findPOJOBuilderConfig(MapperConfig<?> config, AnnotatedClass ac) {
                if (ac.hasAnnotation(JsonPOJOBuilder.class)) {
                    return super.findPOJOBuilderConfig(config, ac);
                }
                // Immutables-generated Builder classes have no @JsonPOJOBuilder annotation
                // and use no setter prefix (methods named like businessId(), not withBusinessId())
                return new JsonPOJOBuilder.Value("build", "");
            }

            @Override
            public boolean hasIgnoreMarker(MapperConfig<?> config, AnnotatedMember m) {
                // In Immutables Builder classes, skip addXxx, addAllXxx, and from() methods.
                // These are collection helper methods that conflict with the direct Iterable setters
                // when using empty builder prefix.
                String className = m.getDeclaringClass().getSimpleName();
                if ("Builder".equals(className)) {
                    String name = m.getName();
                    if ("from".equals(name) || name.startsWith("add")) {
                        return true;
                    }
                }
                return super.hasIgnoreMarker(config, m);
            }
        });
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
