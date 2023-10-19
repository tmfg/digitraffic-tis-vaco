package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class VacoApplicationTests extends SpringBootIntegrationTestBase {

    @Test
    void contextLoads() {
        assertDoesNotThrow(() -> {}, "Spring context is up and running!");
    }

}
