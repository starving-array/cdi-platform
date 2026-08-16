package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.RequestAnalysisCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
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
 * Unit tests for UC-02 RequestAnalysis. Uses fake ports only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started.
 */
class RequestAnalysisHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final ChangeId changeId = ChangeId.generate();

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeJobQueuePort jobQueuePort;
  private RequestAnalysisHandler handler;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    jobQueuePort = new FakeJobQueuePort();
    handler = new RequestAnalysisHandler(
        changeRepository, analysisRunRepository, jobQueuePort, CLOCK);
  }

  @Test
  void noRunExistsForCurrentCommitCreatesQueuedRunAndEnqueuesOnce() {
    openChange("abc123sha");
    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertTrue(result.created());
    assertNotNull(result.analysisRunId());
    assertEquals(1, analysisRunRepository.savedRuns.size());

    AnalysisRun saved = analysisRunRepository.savedRuns.get(0);
    assertEquals(AnalysisRun.Status.QUEUED, saved.getStatus());
    assertEquals(changeId, saved.getChangeId());
    assertEquals(new CodeSnapshot("abc123sha", "feature-x"), saved.getCodeSnapshot());
    assertEquals(CLOCK.instant(), saved.getCreatedAt());

    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("AnalyzeChangeCommand", jobQueuePort.commandNames.get(0));
    assertEquals(result.analysisRunId(),
        ((AnalyzeChangeCommand) jobQueuePort.payloads.get(0)).analysisRunId());
    assertEquals(new IdempotencyKey(
        tenantId.value() + ":" + changeId.value() + ":abc123sha"),
        jobQueuePort.keys.get(0));
  }

  @Test
  void queuedRunIsReturnedWithoutEnqueue() {
    AnalysisRun run = queuedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");

    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertEquals(0, jobQueuePort.commandNames.size());
    assertTrue(analysisRunRepository.savedRuns.isEmpty());
  }

  @Test
  void runningRunIsReturnedWithoutEnqueue() {
    AnalysisRun run = runningRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");

    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertEquals(AnalysisRun.Status.RUNNING, run.getStatus());
    assertEquals(0, jobQueuePort.commandNames.size());
  }

  @Test
  void completedRunIsReturnedWithoutEnqueue() {
    AnalysisRun run = completedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");

    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertEquals(AnalysisRun.Status.COMPLETED, run.getStatus());
    assertEquals(0, jobQueuePort.commandNames.size());
  }

  @Test
  void failedRunIsRetriedBackToQueuedAndReEnqueuedExactlyOnce() {
    AnalysisRun run = failedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");

    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertEquals(AnalysisRun.Status.QUEUED, run.getStatus());
    assertTrue(run.getFailureInfo().isEmpty());
    assertTrue(run.getCompletedAt().isEmpty());
    assertEquals(1, analysisRunRepository.savedRuns.size());
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals(run.getId(),
        ((AnalyzeChangeCommand) jobQueuePort.payloads.get(0)).analysisRunId());
  }

  @Test
  void supersededRunIsReturnedAsHistoricalFactWithoutEnqueueOrMutation() {
    AnalysisRun run = supersededRun("oldsha");
    analysisRunRepository.runs.put(runKey(changeId, "oldsha"), run);
    openChange("newsha");

    IdempotentCommandResult result = handler.handle(command("oldsha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertEquals(AnalysisRun.Status.SUPERSEDED, run.getStatus());
    assertEquals(0, jobQueuePort.commandNames.size());
    assertTrue(analysisRunRepository.savedRuns.isEmpty());
  }

  @Test
  void repeatedIdenticalRequestResolvesSameRunWithoutDuplicates() {
    AnalysisRun run = queuedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");

    IdempotentCommandResult first = handler.handle(command("abc123sha"));
    IdempotentCommandResult second = handler.handle(command("abc123sha"));

    assertFalse(first.created());
    assertFalse(second.created());
    assertEquals(first.analysisRunId(), second.analysisRunId());
    assertEquals(0, jobQueuePort.commandNames.size());
    assertEquals(1, analysisRunRepository.runs.size());
  }

  @Test
  void missingChangeIsRejectedWithChangeNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void mergedChangeIsRejected() {
    Change merged = openChange("abc123sha");
    merged.merge(NOW);
    changeRepository.savedChanges.set(0, merged);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
    assertEquals(changeId.value().toString(), ex.getDetails().get("changeId"));
  }

  @Test
  void closedChangeIsRejected() {
    Change closed = openChange("abc123sha");
    closed.close(NOW);
    changeRepository.savedChanges.set(0, closed);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void nonCurrentCommitWithNoRunIsRejectedAsSuperseded() {
    openChange("newsha");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command("oldsha")));

    assertEquals(ApplicationError.ANALYSIS_SUPERSEDED, ex.getError());
    assertEquals(0, analysisRunRepository.savedRuns.size());
    assertEquals(0, jobQueuePort.commandNames.size());
  }

  @Test
  void disallowedRoleIsRejected() {
    Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(admin, "abc123sha")));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void systemWorkerIsRejectedForRequestAnalysis() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(worker, "abc123sha")));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void changeLookupFailurePropagates() {
    changeRepository.failFindById = true;

    PortException ex = assertThrows(PortException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(PortType.CHANGE_REPOSITORY, ex.getPort());
  }

  @Test
  void queueFailurePropagatesOnCreate() {
    jobQueuePort.failEnqueue = true;
    openChange("abc123sha");

    PortException ex = assertThrows(PortException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(PortType.JOB_QUEUE, ex.getPort());
  }

  @Test
  void queueFailurePropagatesOnRetry() {
    AnalysisRun run = failedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), run);
    openChange("abc123sha");
    jobQueuePort.failEnqueue = true;

    PortException ex = assertThrows(PortException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(PortType.JOB_QUEUE, ex.getPort());
  }

  @Test
  void analysisRunLookupFailurePropagates() {
    openChange("abc123sha");
    analysisRunRepository.failLookup = true;

    PortException ex = assertThrows(PortException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(PortType.ANALYSIS_RUN_REPOSITORY, ex.getPort());
  }

  @Test
  void saveFailureWithConcurrentWinnerReturnsWinnerWithoutEnqueue() {
    openChange("abc123sha");
    AnalysisRun winner = queuedRun("abc123sha");
    analysisRunRepository.runs.put(runKey(changeId, "abc123sha"), winner);
    analysisRunRepository.failSave = true;

    IdempotentCommandResult result = handler.handle(command("abc123sha"));

    assertFalse(result.created());
    assertEquals(winner.getId(), result.analysisRunId());
    assertEquals(0, jobQueuePort.commandNames.size());
    assertEquals(0, analysisRunRepository.savedRuns.size());
    assertEquals(AnalysisRun.Status.QUEUED, winner.getStatus());
  }

  @Test
  void saveFailureWithoutConcurrentWinnerPropagates() {
    openChange("abc123sha");
    analysisRunRepository.failSave = true;

    PortException ex = assertThrows(PortException.class,
        () -> handler.handle(command("abc123sha")));

    assertEquals(PortType.ANALYSIS_RUN_REPOSITORY, ex.getPort());
    assertEquals(0, jobQueuePort.commandNames.size());
  }

  @Test
  void sameChangeAndCommitInDifferentTenantsCreateSeparateRuns() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();
    openChangeFor(tenantA, "abc123sha");
    openChangeFor(tenantB, "abc123sha");
    AnalysisRun runA = queuedRun("abc123sha");
    analysisRunRepository.runs.put(
        tenantA.value() + "|" + changeId.value() + "|abc123sha", runA);

    IdempotentCommandResult resultB = handler.handle(command(tenantB, engineer, "abc123sha"));

    assertTrue(resultB.created());
    assertFalse(resultB.analysisRunId().equals(runA.getId()));
    assertEquals(AnalysisRun.Status.QUEUED, runA.getStatus());
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals(resultB.analysisRunId(),
        ((AnalyzeChangeCommand) jobQueuePort.payloads.get(0)).analysisRunId());
    assertEquals(AnalysisRun.Status.QUEUED,
        analysisRunRepository.runs.get(
            tenantB.value() + "|" + changeId.value() + "|abc123sha").getStatus());
  }

  @Test
  void changeOfAnotherTenantIsNotFound() {
    TenantId tenantOther = TenantId.generate();
    openChange("abc123sha");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantOther, engineer, "abc123sha")));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  private Change openChange(String commitSha) {
    return openChangeFor(tenantId, commitSha);
  }

  private Change openChangeFor(TenantId tenant, String commitSha) {
    Change change = new Change(
        changeId, repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", commitSha, NOW);
    changeRepository.byId.put(tenant.value() + "|" + changeId.value(), change);
    changeRepository.savedChanges.add(change);
    return change;
  }

  private AnalysisRun queuedRun(String commitSha) {
    return new AnalysisRun(
        AnalysisRunId.generate(), changeId,
        new CodeSnapshot(commitSha, "feature-x"), NOW);
  }

  private AnalysisRun runningRun(String commitSha) {
    AnalysisRun run = queuedRun(commitSha);
    run.start();
    return run;
  }

  private AnalysisRun completedRun(String commitSha) {
    AnalysisRun run = queuedRun(commitSha);
    run.start();
    run.complete(NOW.plusSeconds(30));
    return run;
  }

  private AnalysisRun failedRun(String commitSha) {
    AnalysisRun run = queuedRun(commitSha);
    run.start();
    run.fail(new AnalysisFailure(
        AnalysisFailure.FailureCategory.ANALYSIS_FAILED, "x", NOW.plusSeconds(5)));
    return run;
  }

  private AnalysisRun supersededRun(String commitSha) {
    AnalysisRun run = queuedRun(commitSha);
    run.start();
    run.supersede();
    return run;
  }

  private RequestAnalysisCommand command(String commitSha) {
    return command(engineer, commitSha);
  }

  private RequestAnalysisCommand command(Actor actor, String commitSha) {
    return new RequestAnalysisCommand(
        tenantId, changeId, commitSha, actor, new IdempotencyKey("header-key-1"));
  }

  private RequestAnalysisCommand command(TenantId tenant, Actor actor, String commitSha) {
    return new RequestAnalysisCommand(
        tenant, changeId, commitSha, actor, new IdempotencyKey("header-key-1"));
  }

  private String runKey(ChangeId changeId, String commitSha) {
    return tenantId.value() + "|" + changeId.value() + "|" + commitSha;
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<String, Change> byId = new HashMap<>();
    final List<Change> savedChanges = new ArrayList<>();
    boolean failFindById;

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return Optional.empty();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      if (failFindById) {
        throw new PortException(PortType.CHANGE_REPOSITORY, false, "change db down");
      }
      return Optional.ofNullable(byId.get(key(tenantId, changeId)));
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      savedChanges.add(change);
      byId.put(key(tenantId, change.getId()), change);
      return change;
    }

    private static String key(TenantId tenantId, ChangeId changeId) {
      return tenantId.value() + "|" + changeId.value();
    }
  }

  private class FakeAnalysisRunRepository implements AnalysisRunRepository {
    final Map<String, AnalysisRun> runs = new HashMap<>();
    final List<AnalysisRun> savedRuns = new ArrayList<>();
    boolean failLookup;
    boolean failSave;

    @Override
    public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
        TenantId tenantId, ChangeId changeId, String commitSha) {
      if (failLookup) {
        throw new PortException(
            PortType.ANALYSIS_RUN_REPOSITORY, false, "analysis run db down");
      }
      return Optional.ofNullable(runs.get(
          tenantId.value() + "|" + changeId.value() + "|" + commitSha));
    }

    @Override
    public Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId) {
      return runs.values().stream()
          .filter(r -> r.getId().equals(analysisRunId))
          .findFirst()
          .map(run -> new AnalysisRunContext(tenantId, run));
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      AnalysisRun run = runs.values().stream()
          .filter(r -> r.getId().equals(analysisRunId))
          .findFirst().orElse(null);
      if (run == null || run.getStatus() != AnalysisRun.Status.QUEUED) {
        return false;
      }
      run.start();
      return true;
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      if (failSave) {
        throw new PortException(PortType.ANALYSIS_RUN_REPOSITORY, false,
            "unique (tenant, change, commit) violation");
      }
      savedRuns.add(run);
      runs.put(tenantId.value() + "|" + run.getChangeId().value() + "|"
          + run.getCodeSnapshot().commitSha(), run);
      return run;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(
        TenantId tenantId, ChangeId changeId) {
      return List.of();
    }
  }

  private static class FakeJobQueuePort implements JobQueuePort {
    final List<String> commandNames = new ArrayList<>();
    final List<Object> payloads = new ArrayList<>();
    final List<IdempotencyKey> keys = new ArrayList<>();
    boolean failEnqueue;

    @Override
    public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
      if (failEnqueue) {
        throw new PortException(PortType.JOB_QUEUE, true, "queue down");
      }
      commandNames.add(commandName);
      payloads.add(payload);
      keys.add(key);
      return new JobId("job-1");
    }

    @Override
    public void cancel(IdempotencyKey key) {
      // not used by RequestAnalysis
    }
  }
}