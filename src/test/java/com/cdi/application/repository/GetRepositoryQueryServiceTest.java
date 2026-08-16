package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetRepositoryQuery;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for UC-14 GetRepository. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. Verifies the full aggregate read for both ENGINEER and
 * TENANT_ADMIN actors, archived-status preservation, missing-tenant and
 * cross-tenant {@code REPOSITORY_NOT_FOUND}, and SYSTEM_WORKER role rejection.
 */
class GetRepositoryQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakeRepositoryRepository repositoryRepository;
  private GetRepositoryQueryService service;

  @BeforeEach
  void setUp() {
    repositoryRepository = new FakeRepositoryRepository();
    service = new GetRepositoryQueryService(repositoryRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    Repository seeded = repositoryRepository.seed(tenantId, "core-backend");

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
    assertEquals(seeded.getCreatedAt(), repository.getCreatedAt());
    assertEquals(seeded.getUpdatedAt(), repository.getUpdatedAt());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    Repository seeded = repositoryRepository.seed(tenantId, "core-backend");

    Repository repository = service.handle(
        new GetRepositoryQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), repository.getId());
    assertEquals("core-backend", repository.getName());
  }

  @Test
  void preservesArchivedStatus() {
    Repository seeded = repositoryRepository.seed(tenantId, "core-backend");
    seeded.archive();

    Repository repository = service.handle(
        new GetRepositoryQuery(seeded.getId()), tenantId, engineer);

    assertEquals(Repository.Status.ARCHIVED, repository.getStatus());
  }

  @Test
  void missingRepositoryRaisesRepositoryNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetRepositoryQuery(RepositoryId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantRepositoryIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    Repository seeded = repositoryRepository.seed(tenantB, "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetRepositoryQuery(seeded.getId()), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    Repository seeded = repositoryRepository.seed(tenantId, "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetRepositoryQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private static class FakeRepositoryRepository implements RepositoryRepository {
    final Map<RepositoryId, Repository> byId = new HashMap<>();

    Repository seed(TenantId tenantId, String name) {
      Repository repository = new Repository(
          RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-123",
          name, "https://github.com/acme/" + name, "main",
          Instant.parse("2026-08-14T12:00:00Z"));
      byId.put(repository.getId(), repository);
      return repository;
    }

    @Override
    public Optional<Repository> findByTenantIdProviderTypeExternalId(
        TenantId tenantId, Repository.ProviderType providerType, String externalId) {
      return byId.values().stream()
          .filter(r -> r.getTenantId().equals(tenantId)
              && r.getProviderType() == providerType
              && r.getExternalId().equals(externalId))
          .findFirst();
    }

    @Override
    public Optional<Repository> findByTenantIdAndId(
        TenantId tenantId, RepositoryId repositoryId) {
      return byId.values().stream()
          .filter(r -> r.getTenantId().equals(tenantId) && r.getId().equals(repositoryId))
          .findFirst();
    }

    @Override
    public Repository save(Repository repository) {
      byId.put(repository.getId(), repository);
      return repository;
    }
  }
}