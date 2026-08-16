package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreateRepositoryResult;
import com.cdi.application.port.in.CreateRepositoryCommand;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-08 CreateRepository. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. UC-08 emits no domain event, so (unlike UC-07) there is no
 * event-publisher fake here.
 */
class CreateRepositoryHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  // Fresh per test method (JUnit Jupiter creates a new test instance per
  // method); all command(...) helpers below use this same tenantId so that
  // idempotency is exercised within one tenant scope.
  private final TenantId tenantId = TenantId.generate();

  private FakeRepositoryRepository repositoryRepository;
  private CreateRepositoryHandler handler;

  @BeforeEach
  void setUp() {
    repositoryRepository = new FakeRepositoryRepository();
    handler = new CreateRepositoryHandler(repositoryRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminCreation() {
    CreateRepositoryResult result = handler.handle(command("ext-1", "core-backend"));

    assertTrue(result.created());
    assertNotNull(result.repositoryId());
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void generatedRepositoryIdIsReturned() {
    CreateRepositoryResult result = handler.handle(command("ext-1", "core-backend"));

    Repository saved = repositoryRepository.savedRepositories.get(0);
    assertEquals(saved.getId(), result.repositoryId());
    assertNotNull(result.repositoryId().value());
  }

  @Test
  void createdRepositoryStartsActive() {
    handler.handle(command("ext-1", "core-backend"));

    Repository saved = repositoryRepository.savedRepositories.get(0);
    assertEquals(Repository.Status.ACTIVE, saved.getStatus());
  }

  @Test
  void allFieldsPreservedOnCreation() {
    CreateRepositoryCommand command = command("ext-1", "core-backend");
    handler.handle(command);

    Repository saved = repositoryRepository.savedRepositories.get(0);
    assertEquals(command.tenantId(), saved.getTenantId());
    assertEquals(Repository.ProviderType.GITHUB, saved.getProviderType());
    assertEquals("ext-1", saved.getExternalId());
    assertEquals("core-backend", saved.getName());
    assertEquals("https://github.com/acme/core-backend", saved.getUrl());
    assertEquals("main", saved.getDefaultBranch());
    assertEquals(NOW, saved.getCreatedAt());
    assertEquals(NOW, saved.getUpdatedAt());
  }

  @Test
  void unauthorizedActorRejected() {
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    CreateRepositoryCommand engineerCommand = command(engineer, "ext-1", "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(engineerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(repositoryRepository.savedRepositories.isEmpty());
  }

  @Test
  void systemWorkerRoleRejected() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    CreateRepositoryCommand workerCommand = command(worker, "ext-1", "core-backend");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(workerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void invalidCommandRejectedByContract() {
    assertThrows(DomainException.class, () -> command("  ", "core-backend"));
    assertThrows(DomainException.class, () -> command("ext-1", "  "));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, Repository.ProviderType.GITHUB, "ext-1", "name",
        "  ", "main", tenantAdmin, new IdempotencyKey("key-1")));
  }

  @Test
  void duplicateRepositoryIsReusedIdempotently() {
    Repository existing = repositoryRepository.seed(
        tenantId, Repository.ProviderType.GITHUB, "ext-1");

    CreateRepositoryResult result = handler.handle(command("ext-1", "core-backend"));

    assertFalse(result.created());
    assertEquals(existing.getId(), result.repositoryId());
    assertTrue(repositoryRepository.savedRepositories.isEmpty());
  }

  @Test
  void repeatedInvocationDoesNotSave() {
    handler.handle(command("ext-1", "core-backend"));
    assertEquals(1, repositoryRepository.savedRepositories.size());

    handler.handle(command("ext-1", "core-backend"));
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void differentNaturalKeysCreateDistinctRepositories() {
    handler.handle(command("ext-1", "core-backend"));
    handler.handle(command("ext-2", "infra-service"));

    assertEquals(2, repositoryRepository.savedRepositories.size());
  }

  @Test
  void duplicateLookupIsTenantScoped() {
    // Same (provider, externalId) under a different tenant does NOT match —
    // it is a distinct repository, not an idempotent reuse.
    TenantId otherTenant = TenantId.generate();
    repositoryRepository.seed(otherTenant, Repository.ProviderType.GITHUB, "ext-1");

    CreateRepositoryResult result = handler.handle(command("ext-1", "core-backend"));

    assertTrue(result.created());
    assertEquals(1, repositoryRepository.savedRepositories.size());
  }

  @Test
  void persistenceFailurePropagates() {
    repositoryRepository.throwOnSave = true;

    assertThrows(RuntimeException.class, () -> handler.handle(command("ext-1", "core-backend")));
  }

  private CreateRepositoryCommand command(String externalId, String name) {
    return command(tenantAdmin, externalId, name);
  }

  private CreateRepositoryCommand command(Actor actor, String externalId, String name) {
    return new CreateRepositoryCommand(
        tenantId, Repository.ProviderType.GITHUB, externalId, name,
        "https://github.com/acme/" + name, "main", actor, new IdempotencyKey("header-key-1"));
  }

  private static class FakeRepositoryRepository implements RepositoryRepository {
    final Map<String, Repository> repositories = new HashMap<>();
    final List<Repository> savedRepositories = new ArrayList<>();
    boolean throwOnSave = false;

    @Override
    public Optional<Repository> findByTenantIdProviderTypeExternalId(
        TenantId tenantId, Repository.ProviderType providerType, String externalId) {
      return Optional.ofNullable(repositories.get(key(tenantId, providerType, externalId)));
    }

    @Override
    public Optional<Repository> findByTenantIdAndId(
        TenantId tenantId, RepositoryId repositoryId) {
      return repositories.values().stream()
          .filter(r -> r.getTenantId().equals(tenantId) && r.getId().equals(repositoryId))
          .findFirst();
    }

    @Override
    public Repository save(Repository repository) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedRepositories.add(repository);
      repositories.put(
          key(repository.getTenantId(), repository.getProviderType(), repository.getExternalId()),
          repository);
      return repository;
    }

    Repository seed(TenantId tenantId, Repository.ProviderType providerType, String externalId) {
      Repository repo = new Repository(
          RepositoryId.generate(), tenantId, providerType, externalId, "seeded", "url", "main",
          NOW.minusSeconds(3600));
      repositories.put(key(tenantId, providerType, externalId), repo);
      return repo;
    }

    private static String key(
        TenantId tenantId, Repository.ProviderType providerType, String externalId) {
      return tenantId.value() + "|" + providerType.name() + "|" + externalId;
    }
  }
}
