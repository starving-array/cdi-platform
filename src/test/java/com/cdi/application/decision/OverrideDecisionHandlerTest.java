package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.port.in.OverrideDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.common.domain.event.DecisionOverridden;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.RequiredAction;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-06 OverrideDecision — the synchronous TENANT_ADMIN-only
 * one-shot override (application-layer.md §6 UC-06, ADR-006). Uses fake ports
 * only; no Testcontainers, no Spring context, no persistence.
 */
class OverrideDecisionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String COMMIT_SHA = "abc123sha";

  private final TenantId tenantId = TenantId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final DecisionId decisionId = DecisionId.generate();
  private final Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private FakeDomainEventPublisher eventPublisher;
  private OverrideDecisionHandler handler;

  @BeforeEach
  void setUp() {
    analysisRunRepository = new FakeAnalysisRunRepository(tenantId);
    decisionRecordRepository = new FakeDecisionRecordRepository(tenantId);
    eventPublisher = new FakeDomainEventPublisher();
    handler = new OverrideDecisionHandler(
        analysisRunRepository, decisionRecordRepository, eventPublisher, CLOCK);
  }

  @Test
  void tenantAdminOverridesDecisionWithImmutableSupplementalRecord() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    DecisionRecord decision = decisionFor(runId);
    decisionRecordRepository.byRun.put(runId.value(), decision);

    var result = handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "Security incident"));

    assertEquals(decisionId, result.decisionId());
    assertEquals(DecisionOutcome.APPROVE, result.originalOutcome());
    assertEquals(DecisionOutcome.BLOCK, result.outcome());

    DecisionRecord saved = decisionRecordRepository.saved.get(0);
    // Effective outcome reflects the override; the original outcome is retained.
    assertEquals(DecisionOutcome.BLOCK, saved.getOutcome());
    assertEquals(DecisionOutcome.APPROVE, saved.getOriginalOutcome());
    assertTrue(saved.getOverride().isPresent());
    assertEquals("admin-1", saved.getOverride().get().actorId());
    assertEquals("Security incident", saved.getOverride().get().justification());
    assertEquals(NOW, saved.getOverride().get().timestamp());
    assertEquals(DecisionOutcome.APPROVE, saved.getOverride().get().originalOutcome());
    assertEquals(DecisionOutcome.BLOCK, saved.getOverride().get().newOutcome());
  }

  @Test
  void publishedEventCarriesBothOutcomesAndSnapshotContext() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    DecisionRecord decision = decisionFor(runId);
    decisionRecordRepository.byRun.put(runId.value(), decision);

    handler.handle(overrideCommand(decisionId, DecisionOutcome.REVIEW_REQUIRED, "Manual review"));

    assertEquals(1, eventPublisher.events.size());
    DecisionOverridden event = (DecisionOverridden) eventPublisher.events.get(0);
    assertEquals(tenantId, event.tenantId());
    assertEquals(runId, event.analysisRunId());
    assertEquals(changeId, event.changeId());
    assertEquals(decisionId, event.decisionId());
    assertEquals(DecisionOutcome.APPROVE, event.originalOutcome());
    assertEquals(DecisionOutcome.REVIEW_REQUIRED, event.outcome());
    assertEquals(COMMIT_SHA, event.commitSha());
    assertEquals(NOW, event.occurredOn());
  }

  @Test
  void originalDecisionIsNeverMutatedInPlace() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    DecisionRecord decision = decisionFor(runId);
    decisionRecordRepository.byRun.put(runId.value(), decision);

    handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "why"));

    // The source aggregate object is unchanged (only a new instance is saved).
    assertEquals(DecisionOutcome.APPROVE, decision.getOutcome());
    assertTrue(decision.getOverride().isEmpty());
  }

  @Test
  void nonTenantAdminRaisesUnauthorized() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    decisionRecordRepository.byRun.put(runId.value(), decisionFor(runId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "why",
            new Actor("engineer-1", Actor.Role.ENGINEER))));
    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void missingRunRaisesAnalysisRunNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "why")));
    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
  }

  @Test
  void crossTenantRunRaisesAnalysisRunNotFound() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new OverrideDecisionCommand(
            TenantId.generate(), admin, runId, decisionId, DecisionOutcome.BLOCK, "why")));
    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void supersededRunRaisesAnalysisSuperseded() {
    AnalysisRun run = supersededRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "why")));
    assertEquals(ApplicationError.ANALYSIS_SUPERSEDED, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
  }

  @Test
  void missingDecisionRaisesDecisionNotFound() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "why")));
    assertEquals(ApplicationError.DECISION_NOT_FOUND, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void mismatchedDecisionIdRaisesDecisionNotFound() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    decisionRecordRepository.byRun.put(runId.value(), decisionFor(runId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(DecisionId.generate(), DecisionOutcome.BLOCK, "why")));
    assertEquals(ApplicationError.DECISION_NOT_FOUND, ex.getError());
    assertEquals("decision-id-mismatch", ex.getDetails().get("reason"));
    assertTrue(decisionRecordRepository.saved.isEmpty());
  }

  @Test
  void alreadyOverriddenDecisionRaisesDecisionAlreadyOverridden() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    decisionRecordRepository.byRun.put(runId.value(), overriddenDecisionFor(runId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.APPROVE, "again")));
    assertEquals(ApplicationError.DECISION_ALREADY_OVERRIDDEN, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void repeatedOverrideIsRejectedAndNeverReplayed() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    DecisionRecord decision = decisionFor(runId);
    decisionRecordRepository.byRun.put(runId.value(), decision);

    handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "first"));
    // The fake repo stores the overridden aggregate, so a second attempt sees
    // the override and is rejected (one-shot, non-idempotent).
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(decisionId, DecisionOutcome.BLOCK, "second")));
    assertEquals(ApplicationError.DECISION_ALREADY_OVERRIDDEN, ex.getError());
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, eventPublisher.events.size());
  }

  // ---- helpers ----

  private OverrideDecisionCommand overrideCommand(
      DecisionId targetId, DecisionOutcome newOutcome, String justification) {
    return overrideCommand(targetId, newOutcome, justification, admin);
  }

  private OverrideDecisionCommand overrideCommand(
      DecisionId targetId, DecisionOutcome newOutcome, String justification, Actor actor) {
    return new OverrideDecisionCommand(
        tenantId, actor, runId, targetId, newOutcome, justification);
  }

  private AnalysisRun completedRun(TenantId tenant, AnalysisRunId id) {
    AnalysisRun run = new AnalysisRun(
        id, changeId, new CodeSnapshot(COMMIT_SHA, "feature-x"), NOW);
    run.start();
    run.complete(NOW.plusSeconds(30));
    return run;
  }

  private AnalysisRun supersededRun(TenantId tenant, AnalysisRunId id) {
    AnalysisRun run = new AnalysisRun(
        id, changeId, new CodeSnapshot(COMMIT_SHA, "feature-x"), NOW);
    run.start();
    run.supersede();
    return run;
  }

  private DecisionRecord decisionFor(AnalysisRunId id) {
    return DecisionRecord.builder()
        .id(decisionId)
        .tenantId(tenantId)
        .analysisRunId(id)
        .riskAssessmentId(RiskAssessmentId.generate())
        .policyId(PolicyId.generate())
        .policyVersion("v2.1.0")
        .outcome(DecisionOutcome.APPROVE)
        .reasons(List.of(new DecisionReason("Approve all.", "APPROVE_ALL", List.of())))
        .requiredActions(List.of())
        .generatedAt(NOW.minusSeconds(60))
        .build();
  }

  private DecisionRecord overriddenDecisionFor(AnalysisRunId id) {
    DecisionRecord decision = decisionFor(id);
    return decision.withOverride(new com.cdi.decision.domain.HumanOverride(
        "admin-2", decision.getOriginalOutcome(), DecisionOutcome.BLOCK,
        "already blocked", NOW.minusSeconds(30)));
  }

  // ---- fakes ----

  private static class FakeAnalysisRunRepository implements AnalysisRunRepository {
    private final TenantId defaultTenant;
    final Map<UUID, AnalysisRun> byId = new HashMap<>();

    FakeAnalysisRunRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
        TenantId tenantId, ChangeId changeId, String commitSha) {
      return byId.values().stream()
          .filter(r -> r.getChangeId().equals(changeId)
              && r.getCodeSnapshot().commitSha().equals(commitSha))
          .findFirst();
    }

    @Override
    public Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byId.get(analysisRunId.value()))
          .map(run -> new AnalysisRunContext(defaultTenant, run));
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      return false;
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      byId.put(run.getId().value(), run);
      return run;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(
        TenantId tenantId, ChangeId changeId) {
      return List.of();
    }
  }

  private static class FakeDecisionRecordRepository implements DecisionRecordRepository {
    private final TenantId defaultTenant;
    final Map<UUID, DecisionRecord> byRun = new HashMap<>();
    final List<DecisionRecord> saved = new ArrayList<>();

    FakeDecisionRecordRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public DecisionRecord save(TenantId tenantId, DecisionRecord decisionRecord) {
      saved.add(decisionRecord);
      byRun.put(decisionRecord.getAnalysisRunId().value(), decisionRecord);
      return decisionRecord;
    }

    @Override
    public Optional<DecisionRecord> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
      return Optional.ofNullable(byRun.get(analysisRunId.value()));
    }
  }

  private static class FakeDomainEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
    }
  }
}