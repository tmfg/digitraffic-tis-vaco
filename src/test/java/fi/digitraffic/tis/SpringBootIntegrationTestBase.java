package fi.digitraffic.tis;

import fi.digitraffic.tis.vaco.VacoApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class to extend from when implementing Spring Boot integration tests. Wires Flyway and Testcontainers to Spring
 * Data repositories with all necessary DI.
 */
@Testcontainers
@SpringBootTest(
    classes = VacoApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT
)
public abstract class SpringBootIntegrationTestBase {

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:15-bullseye")
        .withDatabaseName("vaco")
        .withUsername("postgres")
        .withPassword("dwULL632mdJZ");

    // TODO: This works, but is rather verbose and ugly so we might want to replace this with test profile
    @DynamicPropertySource
    public static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.flyway.user", postgreSQLContainer::getUsername);
        registry.add("spring.flyway.password", postgreSQLContainer::getPassword);
        registry.add("spring.flyway.createSchemas", () -> true);
        registry.add("spring.flyway.schemas", () -> "vaco");
        registry.add("spring.flyway.locations", () -> "filesystem:../digitraffic-tis-dbmigrator/db/migrations");
        registry.add("spring.flyway.fail-on-missing-locations", () -> true);
    }
}
