package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.ArchiveRepositoryResult;
import com.cdi.application.port.in.ArchiveRepositoryCommand;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the P2 ArchiveRepository capability. Uses fake ports only at
 * the application boundary; no Testcontainers, no Spring context, no
 * persistence is started.
 */
class ArchiveRepositoryHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakeRepositoryRepository repositoryRepository;
  private ArchiveRepositoryHandler handler;

  @BeforeEach
  void setUp() {
    repositoryRepository = new FakeRepositoryRepository();
    handler = new ArchiveRepositoryHandler(repositoryRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminArchiveTransitionsActiveToArchived() {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main", NOW.minusSeconds(3600));
    repositoryRepository.save(repository);

    ArchiveRepositoryResult result = handler.handle(command(repository.getId()));

    assertEquals(repository.getId(), result.repositoryId());
    assertEquals(Repository.Status.ARCHIVED, result.status());
    assertEquals(2, repositoryRepository.savedRepositories.size());
    assertEquals(Repository.Status.ARCHIVED,
        repositoryRepository.savedRepositories.get(1).getStatus());
  }

  @Test
  void unauthorizedActorRejected() {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main", NOW.minusSeconds(3600));
    repositoryRepository.save(repository);

    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, repository.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void systemWorkerRoleRejected() {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main", NOW.minusSeconds(3600));
    repositoryRepository.save(repository);

    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(worker, repository.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void missingRepositoryRaisesRepositoryNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(RepositoryId.generate())));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
    assertTrue(repositoryRepository.savedRepositories.isEmpty());
  }

  @Test
  void crossTenantRepositoryLookupFailsWithRepositoryNotFound() {
    TenantId otherTenant = TenantId.generate();
    Repository repository = new Repository(
        RepositoryId.generate(), otherTenant, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main", NOW.minusSeconds(3600));
    repositoryRepository.save(repository);

    // Call with tenantId != otherTenant
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new ArchiveRepositoryCommand(tenantId, repository.getId(), tenantAdmin)));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void alreadyArchivedRepositoryRaisesAlreadyArchived() {
    RepositoryId repositoryId = RepositoryId.generate();
    Repository repository = Repository.restore(
        repositoryId, tenantId, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main",
        Repository.Status.ARCHIVED, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
    repositoryRepository.save(repository);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(repositoryId)));

    assertEquals(ApplicationError.REPOSITORY_ALREADY_ARCHIVED, ex.getError());
    assertEquals("already-archived", ex.getDetails().get("reason"));
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void persistenceFailurePropagates() {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        "repo-ext-1", "backend", "https://github.com/org/backend", "main", NOW.minusSeconds(3600));
    repositoryRepository.save(repository);
    repositoryRepository.throwOnSave = true;

    assertThrows(RuntimeException.class, () -> handler.handle(command(repository.getId())));
  }

  private ArchiveRepositoryCommand command(RepositoryId repositoryId) {
    return command(tenantAdmin, repositoryId);
  }

  private ArchiveRepositoryCommand command(Actor actor, RepositoryId repositoryId) {
    return new ArchiveRepositoryCommand(tenantId, repositoryId, actor);
  }

  private static class FakeRepositoryRepository implements RepositoryRepository {
    final Map<String, Repository> repositories = new HashMap<>();
    final java.util.List<Repository> savedRepositories = new java.util.ArrayList<>();
    boolean throwOnSave = false;

    private String key(TenantId tId, RepositoryId rId) {
      return tId.value() + ":" + rId.value();
    }

    @Override
    public Optional<Repository> findByTenantIdProviderTypeExternalId(
        TenantId tenantId, Repository.ProviderType providerType, String externalId) {
      return repositories.values().stream()
          .filter(r -> r.getTenantId().equals(tenantId)
              && r.getProviderType() == providerType
              && r.getExternalId().equals(externalId))
          .findFirst();
    }

    @Override
    public Optional<Repository> findByTenantIdAndId(TenantId tenantId, RepositoryId repositoryId) {
      return Optional.ofNullable(repositories.get(key(tenantId, repositoryId)));
    }

    @Override
    public Repository save(Repository repository) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedRepositories.add(repository);
      repositories.put(key(repository.getTenantId(), repository.getId()), repository);
      return repository;
    }
  }
}
