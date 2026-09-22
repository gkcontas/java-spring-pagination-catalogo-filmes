package com.gkcontas.pagination.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base so every integration test reuses the same container and the same Spring
 * context. That matters more than usual here: the Flyway seed inserts 200k movies and
 * builds a GIN index, so paying for it once per test class would make the suite unusable.
 *
 * <p>The container follows the <em>singleton container</em> pattern: it is started in a
 * static initializer and never handed to the {@code @Testcontainers} JUnit extension.
 * That extension ties a static container's lifecycle to the <em>test class</em> — it
 * stops the container when the class finishes and starts a fresh one, on a new random
 * port, for the next class. Spring, meanwhile, caches the application context across
 * classes, so from the second class onwards the cached connection pool still points at
 * the container that was just destroyed, and every test fails with a connection refused.
 * Starting it once per JVM keeps the two lifecycles aligned. Ryuk still removes the
 * container when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
