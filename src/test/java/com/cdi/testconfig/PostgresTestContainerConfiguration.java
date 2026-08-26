package com.cdi.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers infrastructure for integration tests.
 *
 * <p>Exposes a single {@code pgvector/pgvector:pg16} container as a Spring bean so that
 * all integration tests reuse the same PostgreSQL + pgvector configuration without duplicating
 * container declarations.
 *
 * <p>Via {@link ServiceConnection}, Spring Boot supplies the DataSource from this
 * container, which then drives the Flyway and JPA initialization. The container is
 * started and stopped automatically by the Spring test lifecycle.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    }
}
