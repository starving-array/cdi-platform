package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetAnalysisRunQuery;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.change.domain.Change;
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
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end UC-12 GetAnalysisRun integration test: real JPA adapters
 * (Testcontainers PostgreSQL) wired into {@link GetAnalysisRunQueryService}.
 * Persists the change → analysis_run → risk_assessment → decision_record chain
 * through the existing repository adapters, then verifies the run view,
 * failed-run failure round-trip, missing-run and cross-tenant
 * {@code ANALYSIS_RUN_NOT_FOUND}, ENGINEER role enforcement, and that the
 * latest risk/decision come from the requested run only (application-layer.md
 * §6 UC-12, api-contract.md §3.4).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetAnalysisRunQueryServicePersistenceIntegrationTest {

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private RiskAssessmentRepository riskAssessmentRepository;

  @Autowired
  private DecisionRecordRepository decisionRecordRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();

  private GetAnalysisRunQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetAnalysisRunQueryService(
        analysisRunRepository, riskAssessmentRepository, decisionRecordRepository);
  }

  @Test
  void byIdReturnsPersistedViewWithRunRiskAndDecision() {
    Change change = seedChange("PR-42");
    AnalysisRun run = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.COMPLETED, null);
    RiskAssessment risk = seedRisk(run.getId());
    DecisionRecord decision = seedDecision(run.getId(), risk.getId());

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(run.getId(), view.run().getId());
    assertEquals(AnalysisRun.Status.COMPLETED, view.run().getStatus());
    assertEquals(risk.getId(), view.latestRiskAssessmentId());
    assertEquals(decision.getId(), view.latestDecisionId());
    assertNull(view.failure());
  }

  @Test
  void byIdWithNoArtifactsReturnsNullRiskAndDecision() {
    Change change = seedChange("PR-42");
    AnalysisRun run = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.QUEUED, null);

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(AnalysisRun.Status.QUEUED, view.run().getStatus());
    assertNull(view.latestRiskAssessmentId());
    assertNull(view.latestDecisionId());
    assertNull(view.failure());
  }

  @Test
  void failedRunFailureRoundTrips() {
    Change change = seedChange("PR-42");
    Instant failedAt = Instant.parse("2026-08-15T12:01:00Z");
    AnalysisRun run = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.FAILED,
        new AnalysisFailure(AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE,
            "gh-503", failedAt.truncatedTo(ChronoUnit.MICROS)));

    AnalysisRunView view = service.handle(
        new GetAnalysisRunQuery(run.getId()), tenantId, engineer);

    assertEquals(AnalysisRun.Status.FAILED, view.run().getStatus());
    assertEquals(AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE,
        view.failure().category());
    assertEquals("gh-503", view.failure().failureCode());
    assertEquals(failedAt.truncatedTo(ChronoUnit.MICROS), view.failure().failedAt());
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
    Change change = seedChange("PR-42");
    AnalysisRun run = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.QUEUED, null);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetAnalysisRunQuery(run.getId()), TenantId.generate(), engineer));

    assertEquals(ApplicationError.ANALYSIS_RUN_NOT_FOUND, ex.getError());
  }

  @Test
  void nonEngineerRoleRejected() {
    Change change = seedChange("PR-42");
    AnalysisRun run = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.QUEUED, null);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetAnalysisRunQuery(run.getId()), tenantId,
            new Actor("admin-1", Actor.Role.TENANT_ADMIN)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void latestArtifactsResolvedFromRequestedRunOnly() {
    Change change = seedChange("PR-42");
    AnalysisRun older = seedRun(change, "sha-old", "2026-08-15T10:00:00Z",
        AnalysisRun.Status.COMPLETED, null);
    RiskAssessment olderRisk = seedRisk(older.getId());
    seedDecision(older.getId(), olderRisk.getId());
    AnalysisRun newer = seedRun(change, "sha-new", "2026-08-15T12:00:00Z",
        AnalysisRun.Status.QUEUED, null);

    AnalysisRunView newerView = service.handle(
        new GetAnalysisRunQuery(newer.getId()), tenantId, engineer);
    assertNull(newerView.latestRiskAssessmentId());
    assertNull(newerView.latestDecisionId());

    AnalysisRunView olderView = service.handle(
        new GetAnalysisRunQuery(older.getId()), tenantId, engineer);
    assertEquals(olderRisk.getId(), olderView.latestRiskAssessmentId());
  }

  private Change seedChange(String providerChangeId) {
    Change change = new Change(
        ChangeId.generate(), repositoryId, providerChangeId,
        "Add feature", "desc", "alice", "feature", "main", "sha-new",
        Instant.parse("2026-08-15T12:00:00Z"));
    return changeRepository.save(tenantId, change);
  }

  private AnalysisRun seedRun(Change change, String commitSha, String createdAt,
      AnalysisRun.Status targetStatus, AnalysisFailure failure) {
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.parse(createdAt));
    if (targetStatus == AnalysisRun.Status.RUNNING) {
      run.start();
    } else if (targetStatus == AnalysisRun.Status.COMPLETED) {
      run.start();
      run.complete(Instant.parse(createdAt).plusSeconds(300));
    } else if (targetStatus == AnalysisRun.Status.FAILED) {
      run.start();
      run.fail(failure);
    }
    return analysisRunRepository.save(tenantId, run);
  }

  private RiskAssessment seedRisk(AnalysisRunId runId) {
    RiskAssessment assessment = new RiskAssessment(
        RiskAssessmentId.generate(), runId, RiskScore.of(30), RiskLevel.LOW,
        java.util.List.of(), EvidenceState.EVIDENCE_AVAILABLE, "1.0.0",
        Instant.parse("2026-08-15T12:00:00Z"));
    return riskAssessmentRepository.save(tenantId, assessment);
  }

  private DecisionRecord seedDecision(AnalysisRunId runId, RiskAssessmentId riskId) {
    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenantId)
        .analysisRunId(runId)
        .riskAssessmentId(riskId)
        .policyId(PolicyId.generate())
        .policyVersion("v1.0.0")
        .outcome(DecisionOutcome.APPROVE)
        .reasons(java.util.List.of())
        .requiredActions(java.util.List.of())
        .generatedAt(Instant.parse("2026-08-15T12:05:00Z"))
        .build();
    return decisionRecordRepository.save(tenantId, decision);
  }
}
