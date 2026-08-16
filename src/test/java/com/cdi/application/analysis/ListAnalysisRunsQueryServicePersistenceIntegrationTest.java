package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListAnalysisRunsQuery;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
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
 * End-to-end UC-17 ListAnalysisRuns integration test: real JPA adapters
 * (Testcontainers PostgreSQL) wired into {@link ListAnalysisRunsQueryService}.
 * Persists the change → analysis_run chain through the existing repository
 * adapters, then verifies the run listing, the change-first resolution
 * (missing/cross-tenant {@code CHANGE_NOT_FOUND}), ENGINEER-only enforcement,
 * deterministic oldest→newest ordering, and the empty-result contract for a
 * valid change (application-layer.md §6 UC-17, api-contract.md §3.2). Each
 * test seeds its own fresh tenant id to stay isolated inside the shared
 * (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ListAnalysisRunsQueryServicePersistenceIntegrationTest {

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

  private ListAnalysisRunsQueryService service;

  @BeforeEach
  void setUp() {
    service = new ListAnalysisRunsQueryService(changeRepository, analysisRunRepository);
  }

  @Test
  void listsPersistedRunsOrderedOldestFirst() {
    TenantId tenantId = TenantId.generate();
    Change change = seedChange(tenantId);
    AnalysisRun first = seedRun(tenantId, change, "sha-1", "2026-08-15T08:00:00Z");
    AnalysisRun second = seedRun(tenantId, change, "sha-2", "2026-08-15T10:00:00Z");
    AnalysisRun third = seedRun(tenantId, change, "sha-3", "2026-08-15T12:00:00Z");

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertEquals(List.of(first.getId(), second.getId(), third.getId()),
        runs.stream().map(AnalysisRun::getId).toList());
  }

  @Test
  void returnsEmptyListForValidChangeWithNoRuns() {
    TenantId tenantId = TenantId.generate();
    Change change = seedChange(tenantId);

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertTrue(runs.isEmpty());
  }

  @Test
  void missingChangeRaisesChangeNotFound() {
    TenantId tenantId = TenantId.generate();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListAnalysisRunsQuery(ChangeId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantChangeIsNotVisible() {
    TenantId tenantId = TenantId.generate();
    Change change = seedChange(tenantId);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListAnalysisRunsQuery(change.getId()), TenantId.generate(), engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void nonEngineerRoleRejected() {
    TenantId tenantId = TenantId.generate();
    Change change = seedChange(tenantId);
    seedRun(tenantId, change, "sha-1", "2026-08-15T08:00:00Z");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListAnalysisRunsQuery(change.getId()), tenantId,
            new Actor("admin-1", Actor.Role.TENANT_ADMIN)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void preservesPersistedStatusThroughListing() {
    TenantId tenantId = TenantId.generate();
    Change change = seedChange(tenantId);
    seedRun(tenantId, change, "sha-1", "2026-08-15T08:00:00Z");

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertEquals(1, runs.size());
    assertEquals(AnalysisRun.Status.QUEUED, runs.get(0).getStatus());
    assertEquals("sha-1", runs.get(0).getCodeSnapshot().commitSha());
  }

  private Change seedChange(TenantId tenantId) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-42",
        "Add feature", "desc", "alice", "feature", "main", "sha-new",
        Instant.parse("2026-08-15T12:00:00Z"));
    return changeRepository.save(tenantId, change);
  }

  private AnalysisRun seedRun(TenantId tenantId, Change change, String commitSha, String createdAt) {
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.parse(createdAt));
    return analysisRunRepository.save(tenantId, run);
  }
}