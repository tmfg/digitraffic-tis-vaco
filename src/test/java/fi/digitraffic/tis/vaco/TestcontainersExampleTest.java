package fi.digitraffic.tis.vaco;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Example of how to use Testcontainers in your test.
 */
@Testcontainers
public class TestcontainersExampleTest {
    /**
     * Configure PostgreSQL container for testing. The password doesn't have to match with
     */
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-bullseye")
        .withDatabaseName("testvaco")
        .withUsername("postgres")
        .withPassword("testtesttest");

    /**
     * Inject container as datasource to Spring Boot's internals to make it integrate nicely.
     *
     * @param registry Spring Boot's internal dynamic property registry
     */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.createSchemas", () -> true);
        registry.add("spring.flyway.schemas", postgres::getDatabaseName);
        registry.add("spring.flyway.locations", () -> "filesystem:../db-migrator/db/migrations" );
        registry.add("spring.flyway.fail-on-missing-locations", () -> true );
    }

    @Test
    void itWorks() {
        assertThat(postgres, notNullValue());
        assertThat(postgres.isRunning(), is(true));
    }
}
