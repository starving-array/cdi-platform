package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.result.CreateRepositoryResult;
import com.cdi.application.port.in.CreateRepositoryCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.repository.adapter.out.persistence.RepositoryJpaRepository;
import com.cdi.repository.domain.Repository;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-08 integration test: real {@code RepositoryRepository} JPA
 * adapter (Testcontainers PostgreSQL) wired into {@code CreateRepositoryHandler}.
 * UC-08 emits no domain event, so (unlike UC-07) there is no event-publisher
 * fake here. Verifies the full persist path, cross-process idempotent reuse,
 * and that duplicate natural keys never create a second repository row
 * (application-layer.md §6/§10).
 *
 * <p>A real {@code Organization} (tenant row) is persisted before each test so
 * the V7 {@code FK(tenant_id) REFERENCES tenant(id)} constraint is satisfied.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class CreateRepositoryPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private RepositoryJpaRepository repositoryRowCount;

  private TenantId tenantId;
  private CreateRepositoryHandler handler;

  @BeforeEach
  void setUp() {
    tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "repo-e2e-" + UUID.randomUUID(), Instant.now()));
    handler = new CreateRepositoryHandler(repositoryRepository, CLOCK);
  }

  @Test
  void firstInvocationCreatesRepository() {
    CreateRepositoryCommand command = command("ext-1", "core-backend");

    CreateRepositoryResult result = handler.handle(command);

    assertTrue(result.created());
    assertEquals(
        1, repositoryRowCount.countByTenantIdAndProviderTypeAndExternalId(
            tenantId.value(), "GITHUB", "ext-1"));

    Repository persisted = repositoryRepository.findByTenantIdProviderTypeExternalId(
        tenantId, Repository.ProviderType.GITHUB, "ext-1").orElseThrow();
    assertEquals(result.repositoryId(), persisted.getId());
    assertEquals(Repository.Status.ACTIVE, persisted.getStatus());
    assertEquals(NOW, persisted.getCreatedAt());
    assertEquals(NOW, persisted.getUpdatedAt());
  }

  @Test
  void secondInvocationReturnsSameRepositoryWithCreatedFalse() {
    CreateRepositoryCommand command = command("ext-1", "core-backend");

    CreateRepositoryResult first = handler.handle(command);
    CreateRepositoryResult second = handler.handle(command);

    assertTrue(first.created());
    assertFalse(second.created());
    assertEquals(first.repositoryId(), second.repositoryId());
  }

  @Test
  void exactlyOneRepositoryRowExistsAfterRepeatedInvocation() {
    CreateRepositoryCommand command = command("ext-1", "core-backend");

    handler.handle(command);
    handler.handle(command);
    handler.handle(command);

    assertEquals(
        1, repositoryRowCount.countByTenantIdAndProviderTypeAndExternalId(
            tenantId.value(), "GITHUB", "ext-1"));
  }

  private CreateRepositoryCommand command(String externalId, String name) {
    return new CreateRepositoryCommand(
        tenantId, Repository.ProviderType.GITHUB, externalId, name,
        "https://github.com/acme/" + name, "main",
        new Actor("admin-1", Actor.Role.TENANT_ADMIN), new IdempotencyKey("header-key-1"));
  }
}
