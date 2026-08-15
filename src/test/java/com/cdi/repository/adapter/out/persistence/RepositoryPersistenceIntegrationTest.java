package com.cdi.repository.adapter.out.persistence;

import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.repository.domain.Repository;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code repository} table adapter against a real
 * PostgreSQL Testcontainer. Scenarios mirror the UC-08 persistence contract
 * (application-layer.md §6, data-model.md §3/§5, V7 migration).
 *
 * <p>Because V7 declares {@code FK(tenant_id) REFERENCES tenant(id)}, every
 * repository row requires a pre-existing tenant row. A real {@code Organization}
 * is persisted via the existing {@link OrganizationRepository} adapter before
 * each test (no direct manipulation of the {@code tenant} table).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class RepositoryPersistenceIntegrationTest {

  @Autowired
  private RepositoryRepository adapter;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private RepositoryJpaRepository repositoryRowCount;

  @Test
  void saveAndFindByNaturalKeyRoundTripsAllFields() {
    TenantId tenantId = createTenant();
    Instant now = Instant.parse("2026-08-15T12:00:00Z");
    Repository repository = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-123",
        "core-backend", "https://github.com/acme/core-backend", "main", now);

    adapter.save(repository);

    Repository loaded = adapter.findByTenantIdProviderTypeExternalId(
        tenantId, Repository.ProviderType.GITHUB, "ext-123").orElseThrow();
    assertEquals(repository.getId(), loaded.getId());
    assertEquals(tenantId, loaded.getTenantId());
    assertEquals(Repository.ProviderType.GITHUB, loaded.getProviderType());
    assertEquals("ext-123", loaded.getExternalId());
    assertEquals("core-backend", loaded.getName());
    assertEquals("https://github.com/acme/core-backend", loaded.getUrl());
    assertEquals("main", loaded.getDefaultBranch());
    assertEquals(Repository.Status.ACTIVE, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
    assertEquals(now, loaded.getUpdatedAt());
  }

  @Test
  void findByNaturalKeyReturnsEmptyWhenAbsent() {
    TenantId tenantId = createTenant();

    Optional<Repository> result = adapter.findByTenantIdProviderTypeExternalId(
        tenantId, Repository.ProviderType.GITHUB, "nope");

    assertTrue(result.isEmpty());
  }

  @Test
  void duplicateNaturalKeyViolatesUniqueConstraint() {
    TenantId tenantId = createTenant();
    Repository first = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-dup",
        "first", "https://github.com/acme/first", "main", Instant.now());
    adapter.save(first);

    Repository duplicate = new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-dup",
        "second", "https://github.com/acme/second", "develop", Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(duplicate));
  }

  @Test
  void tenantIsolationPreventsCrossTenantLookup() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();
    Repository repository = new Repository(
        RepositoryId.generate(), tenantA, Repository.ProviderType.GITHUB, "ext-shared",
        "core", "https://github.com/acme/core", "main", Instant.now());
    adapter.save(repository);

    Optional<Repository> onTenantB = adapter.findByTenantIdProviderTypeExternalId(
        tenantB, Repository.ProviderType.GITHUB, "ext-shared");
    assertTrue(onTenantB.isEmpty());

    Repository onTenantA = adapter.findByTenantIdProviderTypeExternalId(
        tenantA, Repository.ProviderType.GITHUB, "ext-shared").orElseThrow();
    assertEquals(repository.getId(), onTenantA.getId());
  }

  @Test
  void sameExternalIdAllowedAcrossDifferentTenants() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();

    adapter.save(new Repository(
        RepositoryId.generate(), tenantA, Repository.ProviderType.GITHUB, "ext-shared",
        "a", "https://github.com/acme/a", "main", Instant.now()));
    adapter.save(new Repository(
        RepositoryId.generate(), tenantB, Repository.ProviderType.GITHUB, "ext-shared",
        "b", "https://github.com/acme/b", "main", Instant.now()));

    Repository a = adapter.findByTenantIdProviderTypeExternalId(
        tenantA, Repository.ProviderType.GITHUB, "ext-shared").orElseThrow();
    Repository b = adapter.findByTenantIdProviderTypeExternalId(
        tenantB, Repository.ProviderType.GITHUB, "ext-shared").orElseThrow();
    assertEquals("a", a.getName());
    assertEquals("b", b.getName());
  }

  @Test
  void sameProviderAndExternalIdAllowedAcrossDifferentTenants() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();

    adapter.save(new Repository(
        RepositoryId.generate(), tenantA, Repository.ProviderType.GITLAB, "ext-99",
        "g-a", "https://gitlab.com/acme/a", "main", Instant.now()));
    adapter.save(new Repository(
        RepositoryId.generate(), tenantB, Repository.ProviderType.GITLAB, "ext-99",
        "g-b", "https://gitlab.com/acme/b", "main", Instant.now()));

    assertEquals("g-a", adapter.findByTenantIdProviderTypeExternalId(
        tenantA, Repository.ProviderType.GITLAB, "ext-99").orElseThrow().getName());
    assertEquals("g-b", adapter.findByTenantIdProviderTypeExternalId(
        tenantB, Repository.ProviderType.GITLAB, "ext-99").orElseThrow().getName());
  }

  @Test
  void nonExistentTenantRejectedByForeignKey() {
    TenantId orphanTenant = TenantId.generate();
    Repository repository = new Repository(
        RepositoryId.generate(), orphanTenant, Repository.ProviderType.GITHUB, "ext-orphan",
        "orphan", "https://github.com/acme/orphan", "main", Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(repository));
  }

  @Test
  void v7MigrationAppliesAndHibernateValidationPasses() {
    // Hibernate ddl-auto: validate runs at context startup. If the
    // RepositoryEntity fields did not exactly match the V7 columns, the
    // @SpringBootTest context would fail to load and this test (and the rest
    // of the suite sharing the context) would not execute. Reaching this
    // assertion with a counted row confirms the migration applied and the
    // entity validates.
    TenantId tenantId = createTenant();
    adapter.save(new Repository(
        RepositoryId.generate(), tenantId, Repository.ProviderType.GITHUB, "ext-validate",
        "validate", "https://github.com/acme/validate", "main", Instant.now()));

    assertTrue(repositoryRowCount.countByTenantId(tenantId.value()) >= 1);
  }

  /**
   * Persists a real tenant row so the repository FK is satisfied. Uses a unique
   * organization name per call to avoid collisions with rows left by other
   * tests (the existing org-integration tests do not clean up between tests).
   */
  private TenantId createTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "repo-test-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }
}
