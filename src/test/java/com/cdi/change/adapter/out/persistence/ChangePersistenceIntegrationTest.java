package com.cdi.change.adapter.out.persistence;

import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code change} table adapter against a real
 * PostgreSQL Testcontainer. Scenarios mirror the UC-01 persistence contract
 * (application-layer.md §6/§10, data-model.md §B/§5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ChangePersistenceIntegrationTest {

  @Autowired
  private ChangeRepository adapter;

  @Test
  void saveAndFindRoundTripsAllFields() {
    TenantId tenant = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix bug", "desc", "alice", "feature-x", "main", "abc123sha", now);

    adapter.save(tenant, change);

    Change loaded = adapter.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    assertEquals(change.getId(), loaded.getId());
    assertEquals(repository, loaded.getRepositoryId());
    assertEquals("PR-42", loaded.getProviderChangeId());
    assertEquals("Fix bug", loaded.getTitle());
    assertEquals("desc", loaded.getDescription());
    assertEquals("alice", loaded.getAuthor());
    assertEquals("feature-x", loaded.getSourceBranch());
    assertEquals("main", loaded.getTargetBranch());
    assertEquals("abc123sha", loaded.getLatestCommitSha());
    assertEquals(Change.Status.OPEN, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
    assertEquals(now, loaded.getUpdatedAt());
  }

  @Test
  void findByNaturalKeyReturnsEmptyWhenAbsent() {
    Optional<Change> result = adapter.findByTenantAndRepositoryAndProvider(
        TenantId.generate(), RepositoryId.generate(), "PR-nope");

    assertTrue(result.isEmpty());
  }

  @Test
  void tenantIsolationPreventsCrossTenantLookup() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());

    adapter.save(tenantA, change);

    assertTrue(adapter.findByTenantAndRepositoryAndProvider(
        tenantB, repository, "PR-42").isEmpty());
    assertEquals(change.getId(), adapter.findByTenantAndRepositoryAndProvider(
        tenantA, repository, "PR-42").orElseThrow().getId());
  }

  @Test
  void newCommitPersistsAcrossSaveCycle() {
    TenantId tenant = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "oldsha", now);
    adapter.save(tenant, change);

    Change loaded = adapter.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    Instant updateTime = now.plusSeconds(60);
    loaded.updateLatestCommit("newsha", updateTime);
    adapter.save(tenant, loaded);

    Change reloaded = adapter.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    assertEquals("newsha", reloaded.getLatestCommitSha());
    assertEquals(updateTime, reloaded.getUpdatedAt());
    assertEquals(Change.Status.OPEN, reloaded.getStatus());
  }

  @Test
  void nonOpenStatusRoundTrips() {
    TenantId tenant = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Instant now = Instant.now();
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", now);
    change.merge(now.plusSeconds(30));
    adapter.save(tenant, change);

    Change loaded = adapter.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    assertEquals(Change.Status.MERGED, loaded.getStatus());
  }

  @Test
  void findByTenantAndIdRoundTripsChange() {
    TenantId tenant = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix bug", "desc", "alice", "feature-x", "main", "abc123sha",
        Instant.parse("2026-08-14T12:00:00Z"));

    adapter.save(tenant, change);

    Change loaded = adapter.findByTenantAndId(tenant, change.getId()).orElseThrow();
    assertEquals(change.getId(), loaded.getId());
    assertEquals("abc123sha", loaded.getLatestCommitSha());
    assertEquals(Change.Status.OPEN, loaded.getStatus());
  }

  @Test
  void findByTenantAndIdReturnsEmptyWhenAbsent() {
    Optional<Change> result = adapter.findByTenantAndId(
        TenantId.generate(), ChangeId.generate());

    assertTrue(result.isEmpty());
  }

  @Test
  void tenantIsolationPreventsCrossTenantByIdLookup() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Change change = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());

    adapter.save(tenantA, change);

    assertTrue(adapter.findByTenantAndId(tenantB, change.getId()).isEmpty());
    assertEquals(change.getId(),
        adapter.findByTenantAndId(tenantA, change.getId()).orElseThrow().getId());
  }

  @Test
  void duplicateNaturalKeyViolatesUniqueConstraint() {
    TenantId tenant = TenantId.generate();
    RepositoryId repository = RepositoryId.generate();
    Change first = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());
    adapter.save(tenant, first);

    Change duplicate = new Change(
        ChangeId.generate(), repository, "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, duplicate));
  }
}