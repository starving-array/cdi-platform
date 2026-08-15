package com.cdi.application.change;

import com.cdi.analysis.adapter.out.persistence.AnalysisRunJpaRepository;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.port.in.ProposeChangeCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.change.adapter.out.persistence.ChangeJpaRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-01 integration test: real {@code ChangeRepository} and
 * {@code AnalysisRunRepository} JPA adapters (Testcontainers PostgreSQL) wired
 * into {@code ProposeChangeHandler}, with fakes only at the event-publisher and
 * job-queue boundaries. Verifies the full persist path, cross-process
 * idempotent reuse, and the new-commit flow (application-layer.md §6/§10).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ProposeChangePersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private ChangeJpaRepository changeRowCount;

  @Autowired
  private AnalysisRunJpaRepository runRowCount;

  private final TenantId tenant = TenantId.generate();
  private final RepositoryId repository = RepositoryId.generate();

  private UserTailEventPublisher eventPublisher;
  private RecordingJobQueue jobQueue;
  private ProposeChangeHandler handler;

  @BeforeEach
  void setUp() {
    eventPublisher = new UserTailEventPublisher();
    jobQueue = new RecordingJobQueue();
    handler = new ProposeChangeHandler(
        changeRepository, analysisRunRepository, eventPublisher, jobQueue, CLOCK);
  }

  @Test
  void proposalPersistsBothAggregatesAndIsIdempotent() {
    ProposeChangeCommand command = command("PR-42", "abc123sha");

    IdempotentCommandResult first = handler.handle(command);

    assertTrue(first.created());
    assertEquals(1, eventPublisher.published);
    assertEquals(1, jobQueue.commandNames.size());

    Change savedChange = changeRepository.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    AnalysisRun savedRun = analysisRunRepository.findByTenantAndChangeAndCommit(
        tenant, savedChange.getId(), "abc123sha").orElseThrow();
    assertEquals(first.analysisRunId(), savedRun.getId());
    assertEquals(Change.Status.OPEN, savedChange.getStatus());
    assertEquals(AnalysisRun.Status.QUEUED, savedRun.getStatus());
    assertEquals(1, changeRowCount.countByTenantId(tenant.value()));
    assertEquals(1, runRowCount.countByTenantIdAndChangeId(
        tenant.value(), savedChange.getId().value()));

    IdempotentCommandResult replay = handler.handle(command);

    assertFalse(replay.created());
    assertEquals(first.analysisRunId(), replay.analysisRunId());
    assertEquals(1, eventPublisher.published);
    assertEquals(1, jobQueue.commandNames.size());
    assertEquals(1, changeRowCount.countByTenantId(tenant.value()));
    assertEquals(1, runRowCount.countByTenantIdAndChangeId(
        tenant.value(), savedChange.getId().value()));
  }

  @Test
  void newCommitOnPersistedOpenChangeCreatesNewRunAndUpdatesLatest() {
    handler.handle(command("PR-42", "oldsha"));

    IdempotentCommandResult second = handler.handle(command("PR-42", "newsha"));

    assertTrue(second.created());
    assertEquals(2, eventPublisher.published);
    assertEquals(2, jobQueue.commandNames.size());

    Change savedChange = changeRepository.findByTenantAndRepositoryAndProvider(
        tenant, repository, "PR-42").orElseThrow();
    assertEquals("newsha", savedChange.getLatestCommitSha());

    AnalysisRun newRun = analysisRunRepository.findByTenantAndChangeAndCommit(
        tenant, savedChange.getId(), "newsha").orElseThrow();
    assertEquals(second.analysisRunId(), newRun.getId());
    assertEquals(1, changeRowCount.countByTenantId(tenant.value()));
    assertEquals(2, runRowCount.countByTenantIdAndChangeId(
        tenant.value(), savedChange.getId().value()));
  }

  private ProposeChangeCommand command(String providerChangeId, String commitSha) {
    return new ProposeChangeCommand(
        tenant, repository, providerChangeId, commitSha, "feature-x",
        "Fix bug", "desc", "alice", new Actor("engineer-1", Actor.Role.ENGINEER),
        new IdempotencyKey("header-key-1"));
  }

  private static class UserTailEventPublisher implements DomainEventPublisher {
    int published = 0;

    @Override
    public void publish(DomainEvent event) {
      published++;
    }
  }

  private static class RecordingJobQueue implements JobQueuePort {
    final List<String> commandNames = new ArrayList<>();

    @Override
    public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
      commandNames.add(commandName);
      return new JobId("job-1");
    }

    @Override
    public void cancel(IdempotencyKey key) {
      // not used by ProposeChange
    }
  }
}