package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.ArchiveRepositoryResult;
import com.cdi.application.port.in.ArchiveRepositoryCommand;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end P2 ArchiveRepository integration test: real
 * {@code RepositoryRepository} JPA adapter (Testcontainers PostgreSQL)
 * wired into {@code ArchiveRepositoryHandler}. Verifies the ACTIVE→ARCHIVED
 * transition round-trips through persistence, repeat archive is rejected
 * after a real write, role enforcement holds, cross-tenant isolation is enforced,
 * and a missing repository resolves to {@code REPOSITORY_NOT_FOUND}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ArchiveRepositoryPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private ArchiveRepositoryHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ArchiveRepositoryHandler(repositoryRepository, CLOCK);
  }

  @Test
  void archivePersistsStatusTransition() {
    TenantId tenantId = seedOrganization("Archive Repo Test Alpha");
    RepositoryId repoId = seedRepository(tenantId, "repo-1", "backend-1");

    ArchiveRepositoryResult result = handler.handle(command(tenantId, repoId));

    assertEquals(repoId, result.repositoryId());
    assertEquals(Repository.Status.ARCHIVED, result.status());

    Repository reloaded = repositoryRepository.findByTenantIdAndId(tenantId, repoId).orElseThrow();
    assertEquals(Repository.Status.ARCHIVED, reloaded.getStatus());
  }

  @Test
  void repeatedArchiveIsRejectedAfterPersistence() {
    TenantId tenantId = seedOrganization("Archive Repo Test Beta");
    RepositoryId repoId = seedRepository(tenantId, "repo-2", "backend-2");
    handler.handle(command(tenantId, repoId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId, repoId)));

    assertEquals(ApplicationError.REPOSITORY_ALREADY_ARCHIVED, ex.getError());
    Repository reloaded = repositoryRepository.findByTenantIdAndId(tenantId, repoId).orElseThrow();
    assertEquals(Repository.Status.ARCHIVED, reloaded.getStatus());
  }

  @Test
  void nonTenantAdminIsRejected() {
    TenantId tenantId = seedOrganization("Archive Repo Test Gamma");
    RepositoryId repoId = seedRepository(tenantId, "repo-3", "backend-3");
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, tenantId, repoId)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    Repository reloaded = repositoryRepository.findByTenantIdAndId(tenantId, repoId).orElseThrow();
    assertEquals(Repository.Status.ACTIVE, reloaded.getStatus());
  }

  @Test
  void missingRepositoryRaisesRepositoryNotFound() {
    TenantId tenantId = seedOrganization("Archive Repo Test Delta");
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId, RepositoryId.generate())));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantRepositoryLookupFailsWithRepositoryNotFound() {
    TenantId tenantA = seedOrganization("Archive Repo Tenant A");
    TenantId tenantB = seedOrganization("Archive Repo Tenant B");
    RepositoryId repoIdA = seedRepository(tenantA, "repo-a", "service-a");

    // Tenant B attempts to archive Tenant A's repo
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantB, repoIdA)));

    assertEquals(ApplicationError.REPOSITORY_NOT_FOUND, ex.getError());
    Repository reloaded = repositoryRepository.findByTenantIdAndId(tenantA, repoIdA).orElseThrow();
    assertEquals(Repository.Status.ACTIVE, reloaded.getStatus());
  }

  private TenantId seedOrganization(String name) {
    Organization organization = new Organization(TenantId.generate(), name, NOW);
    return organizationRepository.save(organization).getId();
  }

  private RepositoryId seedRepository(TenantId tenantId, String externalId, String name) {
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB,
        externalId, name, "https://github.com/org/" + name, "main", NOW);
    return repositoryRepository.save(repository).getId();
  }

  private ArchiveRepositoryCommand command(TenantId tenantId, RepositoryId repositoryId) {
    return command(new Actor("admin-1", Actor.Role.TENANT_ADMIN), tenantId, repositoryId);
  }

  private ArchiveRepositoryCommand command(Actor actor, TenantId tenantId, RepositoryId repositoryId) {
    return new ArchiveRepositoryCommand(tenantId, repositoryId, actor);
  }
}
