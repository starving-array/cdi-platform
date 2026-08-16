package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetAnalysisRunQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-12 GetAnalysisRun. Uses fake ports only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. Verifies the run view, missing-run and cross-tenant
 * {@code ANALYSIS_RUN_NOT_FOUND}, ENGINEER role enforcement, and that the
 * latest risk/decision come from the requested run only (no fallback).
 */
class GetAnalysisRunQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final TenantId tenantId = TenantId.generate();
  private final ChangeId changeId = ChangeId.generate();

  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private GetAnalysisRunQueryService service;

  @BeforeEach
  void setUp() {
    analysisRunRepository = new FakeAnalysisRunRepository(tenantId);
    riskAssessmentRepository = new FakeRiskAssessmentRepository();
    decisionRecordRepository = new FakeDecisionRecordRepository();
    service = new GetAnalysisRunQueryService(
        analysisRunRepository, riskAssessmentRepository, decisionRecordRepository);
  }

  @Test
  void returnsFullViewWithRunRiskAndDecision() {
    AnalysisRun run = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));
    run.start();
    run.complete(Instant.parse("2026-08-15T12:05:00Z"));
    RiskAssessment risk = riskAssessmentRepository.seed(tenantId, run.getId());
    DecisionRecord decision = decisionRecordRepository.seed(tenantId, run.getId(), risk.getId());

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(run.getId(), view.run().getId());
    assertEquals(AnalysisRun.Status.COMPLETED, view.run().getStatus());
    assertEquals(risk.getId(), view.latestRiskAssessmentId());
    assertEquals(decision.getId(), view.latestDecisionId());
    assertNull(view.failure());
  }

  @Test
  void returnsNullRiskDecisionWhenRunHasNoArtifacts() {
    AnalysisRun run = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(run.getId(), view.run().getId());
    assertEquals(AnalysisRun.Status.QUEUED, view.run().getStatus());
    assertNull(view.latestRiskAssessmentId());
    assertNull(view.latestDecisionId());
    assertNull(view.failure());
  }

  @Test
  void failedRunSurfacesFailure() {
    AnalysisRun run = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));
    run.start();
    run.fail(new AnalysisFailure(
        AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, "gh-503",
        Instant.parse("2026-08-15T12:01:00Z")));

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(AnalysisRun.Status.FAILED, view.run().getStatus());
    assertEquals(AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, view.failure().category());
    assertEquals("gh-503", view.failure().failureCode());
    assertNull(view.latestRiskAssessmentId());
    assertNull(view.latestDecisionId());
  }

  @Test
  void missingRunRaisesAnalysisRunNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetAnalysisRunQuery(AnalysisRunId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantRunIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    AnalysisRun run = analysisRunRepository.seed(tenantB, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetAnalysisRunQuery(run.getId()), tenantId, engineer));

    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
  }

  @Test
  void nonEngineerRoleRejected() {
    AnalysisRun run = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new GetAnalysisRunQuery(run.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());

    ApplicationException adminEx = assertThrows(ApplicationException.class,
        () -> service.handle(new GetAnalysisRunQuery(run.getId()), tenantId,
            new Actor("admin-1", Actor.Role.TENANT_ADMIN)));
    assertEquals(ApplicationError.UNAUTHORIZED, adminEx.getError());
  }

  @Test
  void latestArtifactsResolvedFromRequestedRunOnly() {
    AnalysisRun older = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-old", "main"), Instant.parse("2026-08-15T10:00:00Z"));
    older.start();
    older.complete(Instant.parse("2026-08-15T10:05:00Z"));
    RiskAssessment olderRisk = riskAssessmentRepository.seed(tenantId, older.getId());
    decisionRecordRepository.seed(tenantId, older.getId(), olderRisk.getId());

    AnalysisRun newer = analysisRunRepository.seed(tenantId, changeId,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    AnalysisRunView newerView = service.handle(
        new GetAnalysisRunQuery(newer.getId()), tenantId, engineer);
    assertNull(newerView.latestRiskAssessmentId());
    assertNull(newerView.latestDecisionId());

    AnalysisRunView olderView = service.handle(
        new GetAnalysisRunQuery(older.getId()), tenantId, engineer);
    assertEquals(olderRisk.getId(), olderView.latestRiskAssessmentId());
  }

  private static class FakeAnalysisRunRepository implements AnalysisRunRepository {
    private final TenantId defaultTenant;
    final Map<UUID, AnalysisRun> byId = new HashMap<>();
    final Map<UUID, TenantId> runTenants = new HashMap<>();

    FakeAnalysisRunRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    AnalysisRun seed(TenantId tenantId, ChangeId changeId, CodeSnapshot snapshot,
        Instant createdAt) {
      AnalysisRun run = new AnalysisRun(
          AnalysisRunId.generate(), changeId, snapshot, createdAt);
      byId.put(run.getId().value(), run);
      runTenants.put(run.getId().value(), tenantId);
      return run;
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
          .map(run -> new AnalysisRunContext(
              runTenants.getOrDefault(analysisRunId.value(), defaultTenant), run));
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      return false;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(
        TenantId tenantId, ChangeId changeId) {
      return List.of();
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      byId.put(run.getId().value(), run);
      runTenants.put(run.getId().value(), tenantId);
      return run;
    }
  }

  private static class FakeRiskAssessmentRepository implements RiskAssessmentRepository {
    final Map<UUID, RiskAssessment> byRunId = new HashMap<>();

    RiskAssessment seed(TenantId tenantId, AnalysisRunId runId) {
      RiskAssessment assessment = new RiskAssessment(
          RiskAssessmentId.generate(), runId, RiskScore.of(30), RiskLevel.LOW,
          List.of(), EvidenceState.EVIDENCE_AVAILABLE, "1.0.0",
          Instant.parse("2026-08-15T12:00:00Z"));
      byRunId.put(runId.value(), assessment);
      return assessment;
    }

    @Override
    public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
      byRunId.put(riskAssessment.getAnalysisRunId().value(), riskAssessment);
      return riskAssessment;
    }

    @Override
    public Optional<RiskAssessment> findByAnalysisRunId(
        TenantId tenantId, AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byRunId.get(analysisRunId.value()));
    }
  }

  private static class FakeDecisionRecordRepository implements DecisionRecordRepository {
    final Map<UUID, DecisionRecord> byRunId = new HashMap<>();

    DecisionRecord seed(TenantId tenantId, AnalysisRunId runId, RiskAssessmentId riskId) {
      DecisionRecord decision = DecisionRecord.builder()
          .tenantId(tenantId)
          .analysisRunId(runId)
          .riskAssessmentId(riskId)
          .policyId(PolicyId.generate())
          .policyVersion("v1.0.0")
          .outcome(DecisionOutcome.APPROVE)
          .reasons(List.of())
          .requiredActions(List.of())
          .generatedAt(Instant.parse("2026-08-15T12:05:00Z"))
          .build();
      byRunId.put(runId.value(), decision);
      return decision;
    }

    @Override
    public DecisionRecord save(TenantId tenantId, DecisionRecord decisionRecord) {
      byRunId.put(decisionRecord.getAnalysisRunId().value(), decisionRecord);
      return decisionRecord;
    }

    @Override
    public Optional<DecisionRecord> findByAnalysisRunId(
        TenantId tenantId, AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byRunId.get(analysisRunId.value()));
    }
  }
}
