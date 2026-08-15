package com.cdi;

import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class CdiApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts up correctly,
        // including Flyway migrations and Testcontainers Postgres DB.
    }
}
