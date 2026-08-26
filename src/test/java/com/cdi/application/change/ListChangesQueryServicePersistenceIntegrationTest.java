package com.cdi.application.change;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListChangesQuery;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end P2 ListChanges integration test: real {@code ChangeRepository}
 * and {@code RepositoryRepository} JPA adapters (Testcontainers PostgreSQL)
 * wired into {@code ListChangesQueryService}. Verifies tenant isolation,
 * database-level sorting by {@code createdAt DESC, id DESC}, repository
 * filtering, and role enforcement.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ListChangesQueryServicePersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private ListChangesQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService = new ListChangesQueryService(changeRepository, repositoryRepository);
  }

  @Test
  void listChangesReturnsDatabaseOrderedByCreatedAtDescIdDesc() {
    TenantId tenantId = seedOrganization("ListChanges Org Alpha");
    RepositoryId repoId = seedRepository(tenantId, "repo-alpha");

    Change older = seedChange(tenantId, repoId, "PR-10", NOW.minusSeconds(200));
    Change newer = seedChange(tenantId, repoId, "PR-20", NOW.minusSeconds(100));
    Change newest = seedChange(tenantId, repoId, "PR-30", NOW);

    Actor engineer = new Actor("eng-1", Actor.Role.ENGINEER);
    List<Change> changes = queryService.handle(ListChangesQuery.all(), tenantId, engineer);

    assertEquals(3, changes.size());
    assertEquals(newest.getId(), changes.get(0).getId());
    assertEquals(newer.getId(), changes.get(1).getId());
    assertEquals(older.getId(), changes.get(2).getId());
  }

  @Test
  void listChangesScopedToRepository() {
    TenantId tenantId = seedOrganization("ListChanges Org Beta");
    RepositoryId repo1 = seedRepository(tenantId, "repo-beta-1");
    RepositoryId repo2 = seedRepository(tenantId, "repo-beta-2");

    Change c1 = seedChange(tenantId, repo1, "PR-1", NOW.minusSeconds(10));
    seedChange(tenantId, repo2, "PR-2", NOW.minusSeconds(5));

    Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
    List<Change> changes = queryService.handle(
        ListChangesQuery.forRepository(repo1), tenantId, admin);

    assertEquals(1, changes.size());
    assertEquals(c1.getId(), changes.get(0).getId());
  }

  @Test
  void tenantIsolationPreventsCrossTenantChangeListing() {
    TenantId tenantA = seedOrganization("ListChanges Org A");
    TenantId tenantB = seedOrganization("ListChanges Org B");
    RepositoryId repoA = seedRepository(tenantA, "repo-a");
    RepositoryId repoB = seedRepository(tenantB, "repo-b");

    seedChange(tenantA, repoA, "PR-A", NOW);
    Change changeB = seedChange(tenantB, repoB, "PR-B", NOW);

    Actor engineer = new Actor("eng-b", Actor.Role.ENGINEER);
    List<Change> changesB = queryService.handle(ListChangesQuery.all(), tenantB, engineer);

    assertEquals(1, changesB.size());
    assertEquals(changeB.getId(), changesB.get(0).getId());
  }

  @Test
  void missingRepositoryRaisesRepositoryNotFound() {
    TenantId tenantId = seedOrganization("ListChanges Org Gamma");
    Actor engineer = new Actor("eng-1", Actor.Role.ENGINEER);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> queryService.handle(
            ListChangesQuery.forRepository(RepositoryId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void unauthorizedActorRejected() {
    TenantId tenantId = seedOrganization("ListChanges Org Delta");
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> queryService.handle(ListChangesQuery.all(), tenantId, worker));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private TenantId seedOrganization(String name) {
    Organization organization = new Organization(TenantId.generate(), name, NOW);
    return organizationRepository.save(organization).getId();
  }

  private RepositoryId seedRepository(TenantId tenantId, String name) {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        name + "-ext", name, "https://github.com/org/" + name, "main", NOW);
    return repositoryRepository.save(repository).getId();
  }

  private Change seedChange(TenantId tenantId, RepositoryId repoId, String prId, Instant createdAt) {
    Change change = new Change(
        ChangeId.generate(), repoId, prId, "Title " + prId, "Desc", "author",
        "feature", "main", "sha-" + prId, createdAt);
    return changeRepository.save(tenantId, change);
  }
}