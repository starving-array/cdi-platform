package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListAnalysisRunsQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-17 ListAnalysisRuns. Uses fake ports only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. Verifies the change-first resolution (missing/cross-tenant
 * {@code CHANGE_NOT_FOUND}), ENGINEER-only enforcement, deterministic
 * oldest→newest ordering, the empty-result contract for a valid change, and
 * aggregate-state preservation through the listing.
 */
class ListAnalysisRunsQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final TenantId tenantId = TenantId.generate();

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private ListAnalysisRunsQueryService service;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    service = new ListAnalysisRunsQueryService(changeRepository, analysisRunRepository);
  }

  @Test
  void returnsAllRunsForEngineerOrderedOldestFirst() {
    Change change = changeRepository.seed(tenantId);
    AnalysisRun oldest = analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-1", "main"),
        Instant.parse("2026-08-15T08:00:00Z"));
    AnalysisRun middle = analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-2", "main"),
        Instant.parse("2026-08-15T10:00:00Z"));
    AnalysisRun newest = analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-3", "main"),
        Instant.parse("2026-08-15T12:00:00Z"));

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertEquals(List.of(oldest.getId(), middle.getId(), newest.getId()),
        runs.stream().map(AnalysisRun::getId).toList());
  }

  @Test
  void returnsEmptyListForValidChangeWithNoRuns() {
    Change change = changeRepository.seed(tenantId);

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertTrue(runs.isEmpty());
  }

  @Test
  void missingChangeRaisesChangeNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListAnalysisRunsQuery(ChangeId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantChangeIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    Change change = changeRepository.seed(tenantB);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListAnalysisRunsQuery(change.getId()), tenantId, engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void nonEngineerRoleRejected() {
    Change change = changeRepository.seed(tenantId);
    analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-1", "main"),
        Instant.parse("2026-08-15T10:00:00Z"));

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new ListAnalysisRunsQuery(change.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());

    ApplicationException adminEx = assertThrows(ApplicationException.class,
        () -> service.handle(new ListAnalysisRunsQuery(change.getId()), tenantId,
            new Actor("admin-1", Actor.Role.TENANT_ADMIN)));
    assertEquals(ApplicationError.UNAUTHORIZED, adminEx.getError());
  }

  @Test
  void preservesAggregateStateThroughListing() {
    Change change = changeRepository.seed(tenantId);
    AnalysisRun queued = analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-1", "main"),
        Instant.parse("2026-08-15T08:00:00Z"));
    AnalysisRun completed = analysisRunRepository.seed(
        tenantId, change, new CodeSnapshot("sha-2", "main"),
        Instant.parse("2026-08-15T12:00:00Z"));
    completed.start();
    completed.complete(Instant.parse("2026-08-15T12:05:00Z"));

    List<AnalysisRun> runs = service.handle(
        new ListAnalysisRunsQuery(change.getId()), tenantId, engineer);

    assertEquals(2, runs.size());
    assertEquals(AnalysisRun.Status.QUEUED, runs.get(0).getStatus());
    assertEquals(AnalysisRun.Status.COMPLETED, runs.get(1).getStatus());
    assertEquals("sha-2", runs.get(1).getCodeSnapshot().commitSha());
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<UUID, List<Change>> byTenant = new HashMap<>();

    Change seed(TenantId tenantId) {
      Change change = new Change(
          ChangeId.generate(), RepositoryId.generate(), "PR-42",
          "Add feature", "desc", "alice", "feature", "main", "sha-new",
          Instant.parse("2026-08-15T12:00:00Z"));
      byTenant.computeIfAbsent(tenantId.value(), k -> new ArrayList<>()).add(change);
      return change;
    }

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return Optional.empty();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return byTenant.getOrDefault(tenantId.value(), List.of()).stream()
          .filter(c -> c.getId().equals(changeId))
          .findFirst();
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      byTenant.computeIfAbsent(tenantId.value(), k -> new ArrayList<>()).add(change);
      return change;
    }
  }

  private static class FakeAnalysisRunRepository implements AnalysisRunRepository {
    final Map<UUID, Map<UUID, List<AnalysisRun>>> byTenant = new HashMap<>();

    AnalysisRun seed(TenantId tenantId, Change change, CodeSnapshot snapshot, Instant createdAt) {
      AnalysisRun run = new AnalysisRun(
          AnalysisRunId.generate(), change.getId(), snapshot, createdAt);
      byTenant.computeIfAbsent(tenantId.value(), k -> new HashMap<>())
          .computeIfAbsent(change.getId().value(), k -> new ArrayList<>())
          .add(run);
      return run;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(TenantId tenantId, ChangeId changeId) {
      return byTenant.getOrDefault(tenantId.value(), Map.of())
          .getOrDefault(changeId.value(), List.of())
          .stream()
          .sorted((a, b) -> {
            int byTime = a.getCreatedAt().compareTo(b.getCreatedAt());
            return byTime != 0 ? byTime : a.getId().value().compareTo(b.getId().value());
          })
          .map(run -> new AnalysisRunContext(tenantId, run))
          .toList();
    }

    @Override
    public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
        TenantId tenantId, ChangeId changeId, String commitSha) {
      return Optional.empty();
    }

    @Override
    public Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId) {
      return Optional.empty();
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      return false;
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      return run;
    }
  }
}