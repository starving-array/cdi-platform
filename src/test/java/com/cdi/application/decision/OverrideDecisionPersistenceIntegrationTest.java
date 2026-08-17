package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.port.in.OverrideDecisionCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.adapter.out.persistence.DecisionRecordEntity;
import com.cdi.decision.adapter.out.persistence.DecisionRecordJpaRepository;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.HumanOverride;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-06 OverrideDecision integration test: real JPA adapters
 * (Testcontainers PostgreSQL) wired into {@link OverrideDecisionHandler}.
 * Persists the change → analysis_run → risk_assessment → decision_record chain
 * through the existing repository adapters, then verifies the override writes a
 * supplemental {@code human_override} row while the {@code decision_record} row
 * stays immutable, that the effective outcome round-trips, that a repeated
 * override is rejected ({@code DECISION_ALREADY_OVERRIDDEN}), and that
 * cross-tenant and superseded runs are rejected (application-layer.md §6 UC-06,
 * data-model.md §E/V11, ADR-006).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class OverrideDecisionPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String COMMIT_SHA = "sha-override";

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private RiskAssessmentRepository riskAssessmentRepository;

  @Autowired
  private DecisionRecordRepository decisionRecordRepository;

  @Autowired
  private DecisionRecordJpaRepository decisionRecordJpaRepository;

  private final TenantId tenantId = TenantId.generate();
  private final Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private OverrideDecisionHandler handler;
  private RecordingEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    eventPublisher = new RecordingEventPublisher();
    handler = new OverrideDecisionHandler(
        analysisRunRepository, decisionRecordRepository, eventPublisher, CLOCK);
  }

  @Test
  void overridePersistsSupplementalRowAndKeepsDecisionRowImmutable() {
    AnalysisRun run = seedCompletedRun();
    DecisionRecord decision = seedDecision(run.getId());

    var result = handler.handle(overrideCommand(run.getId(), decision.getId(),
        DecisionOutcome.BLOCK, "Blocking after security review"));

    assertEquals(decision.getId(), result.decisionId());
    assertEquals(DecisionOutcome.APPROVE, result.originalOutcome());
    assertEquals(DecisionOutcome.BLOCK, result.outcome());

    // The historical decision_record row is untouched (immutable aggregate).
    DecisionRecordEntity row = decisionRecordJpaRepository
        .findById(decision.getId().value()).orElseThrow();
    assertEquals(DecisionOutcome.APPROVE.name(), row.getOutcome());
    assertEquals(1, row.getReasons().size(), "reasons must be preserved unchanged");
    assertTrue(row.getOverride() != null, "override row must exist");
    assertEquals(tenantId.value(), row.getOverride().getTenantId());
    assertEquals(DecisionOutcome.APPROVE.name(), row.getOverride().getOriginalOutcome());
    assertEquals(DecisionOutcome.BLOCK.name(), row.getOverride().getNewOutcome());
    assertEquals("admin-1", row.getOverride().getActorId());
    assertEquals("Blocking after security review", row.getOverride().getJustification());
    assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), row.getOverride().getOverrideAt());

    // Reloaded aggregate: effective outcome reflects the override.
    DecisionRecord reloaded = decisionRecordRepository
        .findByAnalysisRunId(tenantId, run.getId()).orElseThrow();
    assertEquals(DecisionOutcome.BLOCK, reloaded.getOutcome());
    assertEquals(DecisionOutcome.APPROVE, reloaded.getOriginalOutcome());
    assertTrue(reloaded.getOverride().isPresent());
    HumanOverride override = reloaded.getOverride().get();
    assertEquals(DecisionOutcome.APPROVE, override.originalOutcome());
    assertEquals(DecisionOutcome.BLOCK, override.newOutcome());
    assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), override.timestamp());

    assertEquals(1, eventPublisher.events.size());
  }

  @Test
  void repeatedOverrideIsRejectedAfterPersistence() {
    AnalysisRun run = seedCompletedRun();
    DecisionRecord decision = seedDecision(run.getId());

    handler.handle(overrideCommand(run.getId(), decision.getId(),
        DecisionOutcome.BLOCK, "first override"));

    // One-shot: the second override attempt must be rejected.
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(run.getId(), decision.getId(),
            DecisionOutcome.APPROVE, "second override")));
    assertEquals(ApplicationError.DECISION_ALREADY_OVERRIDDEN, ex.getError());

    assertEquals(1, eventPublisher.events.size());
    DecisionRecordEntity row = decisionRecordJpaRepository
        .findById(decision.getId().value()).orElseThrow();
    assertTrue(row.getOverride() != null);
  }

  @Test
  void crossTenantOverrideIsRejected() {
    AnalysisRun run = seedCompletedRun();
    DecisionRecord decision = seedDecision(run.getId());

    TenantId otherTenant = TenantId.generate();
    OverrideDecisionCommand command = new OverrideDecisionCommand(
        otherTenant, admin, run.getId(), decision.getId(),
        DecisionOutcome.BLOCK, "cross tenant");

    ApplicationException ex =
        assertThrows(ApplicationException.class, () -> handler.handle(command));
    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void supersededRunIsRejected() {
    Change change = seedChange();
    AnalysisRun run = seedSupersededRun(change.getId());

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(run.getId(), DecisionId.generate(),
            DecisionOutcome.BLOCK, "too late")));
    assertEquals(ApplicationError.ANALYSIS_SUPERSEDED, ex.getError());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void missingDecisionRaisesDecisionNotFound() {
    AnalysisRun run = seedCompletedRun();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(overrideCommand(run.getId(), DecisionId.generate(),
            DecisionOutcome.BLOCK, "no decision yet")));
    assertEquals(ApplicationError.DECISION_NOT_FOUND, ex.getError());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void nonTenantAdminIsRejected() {
    AnalysisRun run = seedCompletedRun();
    DecisionRecord decision = seedDecision(run.getId());

    OverrideDecisionCommand command = new OverrideDecisionCommand(
        tenantId, new Actor("engineer-1", Actor.Role.ENGINEER),
        run.getId(), decision.getId(), DecisionOutcome.BLOCK, "nope");

    ApplicationException ex =
        assertThrows(ApplicationException.class, () -> handler.handle(command));
    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(eventPublisher.events.isEmpty());
  }

  // ---- seeding helpers (mirror DecisionRecordPersistenceIntegrationTest) ----

  private Change seedChange() {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-42",
        "Fix", "", "alice", "feature", "main", COMMIT_SHA, NOW.minusSeconds(120));
    return changeRepository.save(tenantId, change);
  }

  private AnalysisRun seedCompletedRun() {
    Change change = seedChange();
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(COMMIT_SHA, "feature"), NOW.minusSeconds(60));
    run.start();
    run.complete(NOW.minusSeconds(30));
    return analysisRunRepository.save(tenantId, run);
  }

  private AnalysisRun seedSupersededRun(ChangeId changeId) {
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), changeId,
        new CodeSnapshot(COMMIT_SHA, "feature"), NOW.minusSeconds(60));
    run.start();
    run.supersede();
    return analysisRunRepository.save(tenantId, run);
  }

  private DecisionRecord seedDecision(AnalysisRunId runId) {
    RiskAssessment assessment = new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        RiskScore.of(65), RiskLevel.HIGH, List.of(),
        EvidenceState.EVIDENCE_AVAILABLE, "1.0.0", NOW.minusSeconds(45));
    riskAssessmentRepository.save(tenantId, assessment);

    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenantId)
        .analysisRunId(runId)
        .riskAssessmentId(assessment.getId())
        .policyId(PolicyId.generate())
        .policyVersion("v2.1.0")
        .outcome(DecisionOutcome.APPROVE)
        .reasons(List.of(
            new com.cdi.decision.domain.DecisionReason("Approve all.", "APPROVE_ALL", List.of())))
        .requiredActions(List.of())
        .generatedAt(NOW.truncatedTo(ChronoUnit.MICROS))
        .build();
    return decisionRecordRepository.save(tenantId, decision);
  }

  private OverrideDecisionCommand overrideCommand(
      AnalysisRunId runId, DecisionId decisionId, DecisionOutcome outcome, String justification) {
    return new OverrideDecisionCommand(tenantId, admin, runId, decisionId, outcome, justification);
  }

  private static class RecordingEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
    }
  }
}