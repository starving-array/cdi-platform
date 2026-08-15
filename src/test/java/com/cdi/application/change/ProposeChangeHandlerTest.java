package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.ProposeChangeCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.ChangeProposed;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.exception.DomainException;
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
 * Unit tests for UC-01 ProposeChange. Uses fake ports only at the application
 * boundary; no Testcontainers, no Spring context, no persistence is started.
 */
class ProposeChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeDomainEventPublisher eventPublisher;
  private FakeJobQueuePort jobQueuePort;
  private ProposeChangeHandler handler;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    eventPublisher = new FakeDomainEventPublisher();
    jobQueuePort = new FakeJobQueuePort();
    handler = new ProposeChangeHandler(
        changeRepository, analysisRunRepository, eventPublisher, jobQueuePort, CLOCK);
  }

  @Test
  void validCommandCreatesChangeInOpenState() {
    IdempotentCommandResult result = handler.handle(command("PR-42", "abc123sha"));

    assertTrue(result.created());
    assertNotNull(result.analysisRunId());
    assertEquals(1, changeRepository.savedChanges.size());

    Change saved = changeRepository.savedChanges.get(0);
    assertEquals(Change.Status.OPEN, saved.getStatus());
    assertEquals(CLOCK.instant(), saved.getCreatedAt());
  }

  @Test
  void tenantIdIsPreservedAcrossThePersistenceBoundary() {
    handler.handle(command("PR-42", "abc123sha"));

    assertEquals(tenantId, changeRepository.savedTenantIds.get(0));
    assertEquals(tenantId, analysisRunRepository.savedTenantIds.get(0));
  }

  @Test
  void repositoryIdIsPreserved() {
    handler.handle(command("PR-42", "abc123sha"));

    assertEquals(repositoryId, changeRepository.savedChanges.get(0).getRepositoryId());
  }

  @Test
  void providerChangeIdIsPreserved() {
    handler.handle(command("PR-42", "abc123sha"));

    assertEquals("PR-42", changeRepository.savedChanges.get(0).getProviderChangeId());
  }

  @Test
  void latestCommitShaIsPreserved() {
    handler.handle(command("PR-42", "abc123sha"));

    assertEquals("abc123sha", changeRepository.savedChanges.get(0).getLatestCommitSha());
  }

  @Test
  void changeIsCreatedWithIdempotencyKeyAndQueuedJobAndEvent() {
    IdempotentCommandResult result = handler.handle(command("PR-42", "abc123sha"));

    assertEquals(new IdempotencyKey(
        tenantId.value() + ":" + repositoryId.value() + ":PR-42:abc123sha"),
        jobQueuePort.keys.get(0));
    assertEquals("AnalyzeChangeCommand", jobQueuePort.commandNames.get(0));
    assertEquals(result.analysisRunId(),
        ((AnalyzeChangeCommand) jobQueuePort.payloads.get(0)).analysisRunId());

    assertEquals(1, eventPublisher.events.size());
    ChangeProposed event = (ChangeProposed) eventPublisher.events.get(0);
    assertEquals(tenantId, event.tenantId());
    assertEquals(changeRepository.savedChanges.get(0).getId(), event.changeId());
    assertEquals(result.analysisRunId(), event.analysisRunId());
  }

  @Test
  void queuedRunIsCreatedForTheNewChange() {
    IdempotentCommandResult result = handler.handle(command("PR-42", "abc123sha"));

    AnalysisRun run = analysisRunRepository.findByTenantAndChangeAndCommit(
        tenantId, changeRepository.savedChanges.get(0).getId(), "abc123sha").orElseThrow();
    assertEquals(result.analysisRunId(), run.getId());
    assertEquals(AnalysisRun.Status.QUEUED, run.getStatus());
    assertEquals(new CodeSnapshot("abc123sha", "feature-x"), run.getCodeSnapshot());
  }

  @Test
  void idempotentRequestReusesExistingRunWithoutDuplicates() {
    Change existing = new Change(
        ChangeId.generate(), repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", "abc123sha", NOW);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), existing.getId(),
        new CodeSnapshot("abc123sha", "feature-x"), NOW);
    changeRepository.changes.put(naturalKey("PR-42"), existing);
    analysisRunRepository.runs.put(runKey(existing.getId(), "abc123sha"), run);

    IdempotentCommandResult result = handler.handle(command("PR-42", "abc123sha"));

    assertFalse(result.created());
    assertEquals(run.getId(), result.analysisRunId());
    assertTrue(changeRepository.savedChanges.isEmpty());
    assertTrue(analysisRunRepository.savedRuns.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void newCommitOnExistingOpenChangeUpdatesTheChangeAndCreatesANewRun() {
    Change existing = new Change(
        ChangeId.generate(), repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", "oldsha", NOW);
    changeRepository.changes.put(naturalKey("PR-42"), existing);

    IdempotentCommandResult result = handler.handle(command("PR-42", "newsha"));

    assertTrue(result.created());
    assertEquals("newsha", existing.getLatestCommitSha());
    assertEquals(1, changeRepository.savedChanges.size());
    assertEquals(1, analysisRunRepository.savedRuns.size());
    assertEquals(1, jobQueuePort.commandNames.size());
  }

  @Test
  void invalidInputIsRejectedByTheExistingCommandContract() {
    assertThrows(DomainException.class, () -> new ProposeChangeCommand(
        null, repositoryId, "PR-42", "abc123sha", "feature-x",
        "Fix", "", "alice", engineer, new IdempotencyKey("key-1")));
  }

  @Test
  void domainInvariantFailureIsSurfacedThroughTheApplicationErrorBoundary() {
    Change merged = new Change(
        ChangeId.generate(), repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", "oldsha", NOW);
    merged.merge(NOW);
    changeRepository.changes.put(naturalKey("PR-42"), merged);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command("PR-42", "newsha")));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
    assertEquals(merged.getId().value().toString(), ex.getDetails().get("changeId"));
  }

  @Test
  void disallowedRoleIsRejected() {
    Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
    ProposeChangeCommand adminCommand = command(admin, "PR-42", "abc123sha");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(adminCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void systemWorkerRoleIsAllowed() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);

    IdempotentCommandResult result = handler.handle(
        command(worker, "PR-42", "abc123sha"));

    assertTrue(result.created());
    assertEquals(1, changeRepository.savedChanges.size());
  }

  private ProposeChangeCommand command(String providerChangeId, String commitSha) {
    return command(engineer, providerChangeId, commitSha);
  }

  private ProposeChangeCommand command(Actor actor, String providerChangeId, String commitSha) {
    return new ProposeChangeCommand(
        tenantId, repositoryId, providerChangeId, commitSha, "feature-x",
        "Fix bug", "desc", "alice", actor, new IdempotencyKey("header-key-1"));
  }

  private String naturalKey(String providerChangeId) {
    return tenantId.value() + "|" + repositoryId.value() + "|" + providerChangeId;
  }

  private String runKey(ChangeId changeId, String commitSha) {
    return tenantId.value() + "|" + changeId.value() + "|" + commitSha;
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<String, Change> changes = new HashMap<>();
    final List<Change> savedChanges = new ArrayList<>();
    final List<TenantId> savedTenantIds = new ArrayList<>();

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return Optional.ofNullable(changes.get(
          tenantId.value() + "|" + repositoryId.value() + "|" + providerChangeId));
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return changes.values().stream()
          .filter(c -> c.getId().equals(changeId))
          .findFirst();
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      savedTenantIds.add(tenantId);
      savedChanges.add(change);
      changes.put(
          tenantId.value() + "|" + change.getRepositoryId().value() + "|"
              + change.getProviderChangeId(),
          change);
      return change;
    }
  }

  private class FakeAnalysisRunRepository implements AnalysisRunRepository {
    final Map<String, AnalysisRun> runs = new HashMap<>();
    final List<AnalysisRun> savedRuns = new ArrayList<>();
    final List<TenantId> savedTenantIds = new ArrayList<>();

    @Override
    public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
        TenantId tenantId, ChangeId changeId, String commitSha) {
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
      savedTenantIds.add(tenantId);
      savedRuns.add(run);
      runs.put(tenantId.value() + "|" + run.getChangeId().value() + "|"
          + run.getCodeSnapshot().commitSha(), run);
      return run;
    }
  }

  private static class FakeDomainEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
    }
  }

  private static class FakeJobQueuePort implements JobQueuePort {
    final List<String> commandNames = new ArrayList<>();
    final List<Object> payloads = new ArrayList<>();
    final List<IdempotencyKey> keys = new ArrayList<>();

    @Override
    public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
      commandNames.add(commandName);
      payloads.add(payload);
      keys.add(key);
      return new JobId("job-1");
    }

    @Override
    public void cancel(IdempotencyKey key) {
      // not used by ProposeChange
    }
  }
}