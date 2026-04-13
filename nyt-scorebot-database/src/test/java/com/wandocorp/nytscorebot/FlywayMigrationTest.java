package com.wandocorp.nytscorebot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots an in-memory H2 database, runs all Flyway migrations, then lets
 * Hibernate validate the resulting schema against the JPA entity model.
 * If validation passes, the migrations are consistent with the entities.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class FlywayMigrationTest {

    @SpringBootApplication
    static class TestConfig {}

    @Test
    void migrationCreatesValidSchema() {
        // Context loads → Flyway ran V1, Hibernate validated the schema
    }
}
