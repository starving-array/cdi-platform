package com.cdi.application.change;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListChangesQuery;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListChangesQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final Actor systemWorker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
  private final TenantId tenantId = TenantId.generate();

  private FakeChangeRepository changeRepository;
  private FakeRepositoryRepository repositoryRepository;
  private ListChangesQueryService queryService;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    repositoryRepository = new FakeRepositoryRepository();
    queryService = new ListChangesQueryService(changeRepository, repositoryRepository);
  }

  @Test
  void engineerCanListAllChangesForTenant() {
    RepositoryId repoId = seedRepository(tenantId, "repo-1");
    Change change1 = seedChange(tenantId, repoId, "PR-1", NOW.minusSeconds(100));
    Change change2 = seedChange(tenantId, repoId, "PR-2", NOW.minusSeconds(50));

    List<Change> results = queryService.handle(ListChangesQuery.all(), tenantId, engineer);

    assertEquals(2, results.size());
    assertEquals(change2.getId(), results.get(0).getId());
    assertEquals(change1.getId(), results.get(1).getId());
  }

  @Test
  void tenantAdminCanListAllChangesForTenant() {
    RepositoryId repoId = seedRepository(tenantId, "repo-1");
    Change change = seedChange(tenantId, repoId, "PR-1", NOW);

    List<Change> results = queryService.handle(ListChangesQuery.all(), tenantId, tenantAdmin);

    assertEquals(1, results.size());
    assertEquals(change.getId(), results.get(0).getId());
  }

  @Test
  void systemWorkerRoleIsRejectedWithUnauthorized() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> queryService.handle(ListChangesQuery.all(), tenantId, systemWorker));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void nullActorThrowsDomainException() {
    assertThrows(DomainException.class,
        () -> queryService.handle(ListChangesQuery.all(), tenantId, null));
  }

  @Test
  void nullTenantIdThrowsDomainException() {
    assertThrows(DomainException.class,
        () -> queryService.handle(ListChangesQuery.all(), null, engineer));
  }

  @Test
  void filteringByRepositoryReturnsOnlyMatchingChanges() {
    RepositoryId repo1 = seedRepository(tenantId, "repo-1");
    RepositoryId repo2 = seedRepository(tenantId, "repo-2");

    Change c1 = seedChange(tenantId, repo1, "PR-1", NOW.minusSeconds(10));
    seedChange(tenantId, repo2, "PR-2", NOW.minusSeconds(5));

    List<Change> results = queryService.handle(
        ListChangesQuery.forRepository(repo1), tenantId, engineer);

    assertEquals(1, results.size());
    assertEquals(c1.getId(), results.get(0).getId());
  }

  @Test
  void filteringByMissingRepositoryRaisesRepositoryNotFound() {
    RepositoryId missingRepo = RepositoryId.generate();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> queryService.handle(ListChangesQuery.forRepository(missingRepo), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void filteringByCrossTenantRepositoryRaisesRepositoryNotFound() {
    TenantId otherTenant = TenantId.generate();
    RepositoryId foreignRepo = seedRepository(otherTenant, "foreign-repo");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> queryService.handle(ListChangesQuery.forRepository(foreignRepo), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void emptyTenantReturnsEmptyList() {
    List<Change> results = queryService.handle(ListChangesQuery.all(), tenantId, engineer);
    assertTrue(results.isEmpty());
  }

  private RepositoryId seedRepository(TenantId tId, String name) {
    Repository repo = new Repository(
        RepositoryId.generate(), tId, Repository.ProviderType.GITHUB,
        name + "-ext", name, "https://github.com/org/" + name, "main", NOW);
    repositoryRepository.save(repo);
    return repo.getId();
  }

  private Change seedChange(TenantId tId, RepositoryId rId, String prId, Instant createdAt) {
    Change change = new Change(
        ChangeId.generate(), rId, prId, "Title " + prId, "Desc", "author",
        "feature", "main", "sha-" + prId, createdAt);
    changeRepository.save(tId, change);
    return change;
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<TenantId, List<Change>> changesByTenant = new HashMap<>();

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return changesByTenant.getOrDefault(tenantId, List.of()).stream()
          .filter(c -> c.getRepositoryId().equals(repositoryId)
              && c.getProviderChangeId().equals(providerChangeId))
          .findFirst();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return changesByTenant.getOrDefault(tenantId, List.of()).stream()
          .filter(c -> c.getId().equals(changeId))
          .findFirst();
    }

    @Override
    public List<Change> findAllByTenantId(TenantId tenantId) {
      return changesByTenant.getOrDefault(tenantId, List.of()).stream()
          .sorted(Comparator.comparing(Change::getCreatedAt).reversed())
          .toList();
    }

    @Override
    public List<Change> findAllByTenantIdAndRepositoryId(
        TenantId tenantId, RepositoryId repositoryId) {
      return changesByTenant.getOrDefault(tenantId, List.of()).stream()
          .filter(c -> c.getRepositoryId().equals(repositoryId))
          .sorted(Comparator.comparing(Change::getCreatedAt).reversed())
          .toList();
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      changesByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(change);
      return change;
    }
  }

  private static class FakeRepositoryRepository implements RepositoryRepository {
    final Map<String, Repository> repositories = new HashMap<>();

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
      repositories.put(key(repository.getTenantId(), repository.getId()), repository);
      return repository;
    }
  }
}