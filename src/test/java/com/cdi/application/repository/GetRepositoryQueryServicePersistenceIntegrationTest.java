package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetRepositoryQuery;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.repository.domain.Repository;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end UC-14 GetRepository integration test: real JPA adapter
 * (Testcontainers PostgreSQL) wired into {@link GetRepositoryQueryService}.
 * Persists the tenant root (Organization, satisfying the V7 FK) and the
 * Repository through the existing ports, then verifies the full aggregate read
 * for both ENGINEER and TENANT_ADMIN actors, archived-status and
 * updated-at round-trip (via {@link Repository#restore}), missing-tenant and
 * cross-tenant {@code REPOSITORY_NOT_FOUND}, and SYSTEM_WORKER role rejection
 * (application-layer.md §6 UC-14, data-model.md §3.A). Each test seeds its own
 * tenant id and a unique repo name to stay isolated inside the shared
 * (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetRepositoryQueryServicePersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-14T12:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-08-15T10:30:00Z");

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private GetRepositoryQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetRepositoryQueryService(repositoryRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    TenantId tenantId = seedTenant("GetRep Alpha");
    Repository seeded = seedRepo(tenantId, "core-backend");

    Repository repository = service.handle(
        new GetRepositoryQuery(seeded.getId()), tenantId, engineer);

    assertEquals(seeded.getId(), repository.getId());
    assertEquals(tenantId, repository.getTenantId());
    assertEquals(Repository.ProviderType.GITHUB, repository.getProviderType());
    assertEquals("ext-123", repository.getExternalId());
    assertEquals("core-backend", repository.getName());
    assertEquals("https://github.com/acme/core-backend", repository.getUrl());
    assertEquals("main", repository.getDefaultBranch());
    assertEquals(Repository.Status.ACTIVE, repository.getStatus());
    assertEquals(CREATED_AT, repository.getCreatedAt());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    TenantId tenantId = seedTenant("GetRep Beta");
    Repository seeded = seedRepo(tenantId, "core-backend");

    Repository repository = service.handle(
        new GetRepositoryQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), repository.getId());
    assertEquals("core-backend", repository.getName());
  }

  @Test
  void preservesArchivedStatus() {
    TenantId tenantId = seedTenant("GetRep Gamma");
    Repository archived = Repository.restore(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-123",
        "archived-repo", "https://github.com/acme/archived-repo", "main",
        Repository.Status.ARCHIVED, CREATED_AT, UPDATED_AT);
    repositoryRepository.save(archived);

    Repository repository = service.handle(
        new GetRepositoryQuery(archived.getId()), tenantId, engineer);

    assertEquals(Repository.Status.ARCHIVED, repository.getStatus());
    assertEquals(UPDATED_AT, repository.getUpdatedAt());
  }

  @Test
  void missingRepositoryRaisesRepositoryNotFound() {
    TenantId tenantId = seedTenant("GetRep Delta");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetRepositoryQuery(RepositoryId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantRepositoryIsNotVisible() {
    TenantId tenantId = seedTenant("GetRep Epsilon");
    Repository seeded = seedRepo(tenantId, "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetRepositoryQuery(seeded.getId()), TenantId.generate(), tenantAdmin));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    TenantId tenantId = seedTenant("GetRep Zeta");
    Repository seeded = seedRepo(tenantId, "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetRepositoryQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private TenantId seedTenant(String name) {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, name + "-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }

  private Repository seedRepo(TenantId tenantId, String name) {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-123",
        name, "https://github.com/acme/" + name, "main", CREATED_AT);
    return repositoryRepository.save(repository);
  }
}