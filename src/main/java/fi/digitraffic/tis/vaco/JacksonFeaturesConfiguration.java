package fi.digitraffic.tis.vaco;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customize Spring's internal Jackson facilities.
 */
@Configuration
public class JacksonFeaturesConfiguration {

    /**
     * Configures underlying {@link com.fasterxml.jackson.databind.ObjectMapper} to be as strict as possible without
     * getting in the way.
     *
     * @return <code>Jackson2ObjectMapperBuilderCustomizer</code> with strict {@link com.fasterxml.jackson.databind.cfg.CoercionConfig}
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictCoercionConfiguration() {
        return jacksonObjectMapperBuilder -> {
            jacksonObjectMapperBuilder.postConfigurer(objectMapper -> {
                // @JsonView made easy
                objectMapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
                // prevent coercion of anything to strings to allow Jakarta Validation to work as intended
                objectMapper.coercionConfigDefaults()
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Array, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Object, CoercionAction.Fail);
            });
        };
    }
}
