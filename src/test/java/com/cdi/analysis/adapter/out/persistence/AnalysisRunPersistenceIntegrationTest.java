package com.cdi.analysis.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code analysis_run} table adapter against a real
 * PostgreSQL Testcontainer. Scenarios mirror the UC-01 persistence contract
 * (application-layer.md §6/§10, data-model.md §B/§5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class AnalysisRunPersistenceIntegrationTest {

  @Autowired
  private AnalysisRunRepository adapter;

  @Autowired
  private ChangeRepository changeRepository;

  private Change requireChange(TenantId tenant) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());
    return changeRepository.save(tenant, change);
  }

  @Test
  void saveAndFindRoundTripsQueuedRun() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);

    adapter.save(tenant, run);

    AnalysisRun loaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    assertEquals(run.getId(), loaded.getId());
    assertEquals(changeId, loaded.getChangeId());
    assertEquals(new CodeSnapshot("abc123sha", "feature-x"), loaded.getCodeSnapshot());
    assertEquals(AnalysisRun.Status.QUEUED, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
    assertTrue(loaded.getCompletedAt().isEmpty());
    assertTrue(loaded.getFailureInfo().isEmpty());
  }

  @Test
  void findBySnapshotReturnsEmptyWhenAbsent() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();

    Optional<AnalysisRun> result = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "nosuchsha");

    assertTrue(result.isEmpty());
  }

  @Test
  void tenantIsolationPreventsCrossTenantLookup() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();
    ChangeId changeId = requireChange(tenantA).getId();
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"),
        Instant.now());

    adapter.save(tenantA, run);

    assertTrue(adapter.findByTenantAndChangeAndCommit(
        tenantB, changeId, "abc123sha").isEmpty());
    assertEquals(run.getId(), adapter.findByTenantAndChangeAndCommit(
        tenantA, changeId, "abc123sha").orElseThrow().getId());
  }

  @Test
  void completionStatePersistsAcrossSaveCycle() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, run);

    AnalysisRun loaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    loaded.start();
    Instant done = now.plusSeconds(90);
    loaded.complete(done);
    adapter.save(tenant, loaded);

    AnalysisRun reloaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    assertEquals(AnalysisRun.Status.COMPLETED, reloaded.getStatus());
    assertEquals(done, reloaded.getCompletedAt().orElseThrow());
  }

  @Test
  void failedRunPersistsFailureInfo() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, run);

    AnalysisRun loaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    loaded.start();
    Instant failedAt = now.plusSeconds(10);
    loaded.fail(new AnalysisFailure(
        AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, "gh-503", failedAt));
    adapter.save(tenant, loaded);

    AnalysisRun reloaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    assertEquals(AnalysisRun.Status.FAILED, reloaded.getStatus());
    AnalysisFailure failure = reloaded.getFailureInfo().orElseThrow();
    assertEquals(AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, failure.category());
    assertEquals("gh-503", failure.failureCode());
    assertEquals(failedAt, failure.failedAt());
  }

  @Test
  void findByIdResolvesRunTogetherWithTenant() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, run);

    AnalysisRunContext context = adapter.findById(run.getId()).orElseThrow();

    assertEquals(tenant, context.tenantId());
    assertEquals(run.getId(), context.run().getId());
  }

  @Test
  void claimTransitionsQueuedToRunningExactlyOnce() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, run);

    assertTrue(adapter.claim(run.getId()));

    AnalysisRun claimed = adapter.findById(run.getId()).orElseThrow().run();
    assertEquals(AnalysisRun.Status.RUNNING, claimed.getStatus());

    assertFalse(adapter.claim(run.getId()));
    assertEquals(AnalysisRun.Status.RUNNING,
        adapter.findById(run.getId()).orElseThrow().run().getStatus());
  }

  @Test
  void claimOnNonQueuedRunIsNoOp() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, run);

    AnalysisRun loaded = adapter.findByTenantAndChangeAndCommit(
        tenant, changeId, "abc123sha").orElseThrow();
    loaded.start();
    loaded.complete(now.plusSeconds(30));
    adapter.save(tenant, loaded);

    assertFalse(adapter.claim(run.getId()));
  }

  @Test
  void duplicateSnapshotViolatesUniqueConstraint() {
    TenantId tenant = TenantId.generate();
    ChangeId changeId = requireChange(tenant).getId();
    Instant now = Instant.now();
    AnalysisRun first = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);
    adapter.save(tenant, first);

    AnalysisRun duplicate = new AnalysisRun(
        AnalysisRunId.generate(), changeId, new CodeSnapshot("abc123sha", "feature-x"), now);

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, duplicate));
  }
}