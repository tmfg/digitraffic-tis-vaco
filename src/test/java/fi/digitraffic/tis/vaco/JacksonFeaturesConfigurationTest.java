package fi.digitraffic.tis.vaco;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonFeaturesConfigurationTest extends SpringBootIntegrationTestBase {

    private final String primitiveValues = """
            {
              "notAnInteger": 123,
              "notAFloat": 45.678,
              "notABoolean": false
            }
            """;

    record Tester(
            String notAnInteger,
            String notAFloat,
            String notABoolean
    ) {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void willNotCoercePrimitivesToString() throws JacksonException {
        List.of("123", "45.6", "false", "true").forEach(primitive -> {
            assertThrows(
                    InvalidFormatException.class,
                    () -> objectMapper.readValue(primitive, String.class),
                    "Should have failed due to unwanted coercion");
        });
    }

    @Test
    void willNotCoercePrimitivePropertiesIntoStringFields() {
        assertThrows(
                InvalidFormatException.class,
                () -> objectMapper.readValue(primitiveValues, Tester.class),
                "Should have failed due to unwanted coercion");
    }
}
