package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetChangeBySourceQuery;
import com.cdi.application.port.in.GetChangeQuery;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-11 GetChange integration test: real JPA adapters
 * (Testcontainers PostgreSQL) wired into {@link GetChangeQueryService}.
 * Persists the change → analysis_run → risk_assessment → decision_record chain
 * through the existing UC-01/UC-03/UC-05 repository adapters, then verifies
 * the aggregated view, both lookup forms, tenant isolation, deterministic run
 * ordering, and the empty-history contract (application-layer.md §6 UC-11).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetChangeQueryServicePersistenceIntegrationTest {

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

  private GetChangeQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetChangeQueryService(
        changeRepository, analysisRunRepository, riskAssessmentRepository,
        decisionRecordRepository);
  }

  @Test
  void byIdAssemblesPersistedViewWithHistoryAndLatestArtifacts() {
    Change change = seedChange("PR-42");
    AnalysisRun oldRun = seedRun(change, "sha-old", "2026-08-15T10:00:00Z");
    AnalysisRun newRun = seedRun(change, "sha-new", "2026-08-15T12:00:00Z");
    RiskAssessment latestRisk = seedRisk(newRun.getId());
    DecisionRecord latestDecision = seedDecision(newRun.getId(), latestRisk.getId());

    ChangeAnalysisView view = service.handle(
        new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(change.getId(), view.change().getId());
    assertEquals(2, view.runs().size());
    assertEquals(oldRun.getId(), view.runs().get(0).getId());
    assertEquals(newRun.getId(), view.runs().get(1).getId());
    assertEquals(latestRisk.getId(), view.latestRisk().getId());
    assertEquals(latestDecision.getId(), view.latestDecision().getId());
  }

  @Test
  void bySourceReferenceAssemblesPersistedView() {
    Change change = seedChange("PR-42");
    seedRun(change, "sha-new", "2026-08-15T12:00:00Z");

    ChangeAnalysisView view = service.handle(
        new GetChangeBySourceQuery(tenantId, repositoryId, "PR-42"), engineer);

    assertEquals(change.getId(), view.change().getId());
    assertEquals(1, view.runs().size());
  }

  @Test
  void byIdChangeNotFoundForMissingChange() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetChangeQuery(ChangeId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantChangeIsNotVisible() {
    seedChange("PR-42");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetChangeBySourceQuery(TenantId.generate(), repositoryId, "PR-42"), engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void emptyHistoryReturnsEmptyRunsAndNullArtifacts() {
    Change change = seedChange("PR-42");

    ChangeAnalysisView view = service.handle(
        new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(change.getId(), view.change().getId());
    assertTrue(view.runs().isEmpty());
    assertNull(view.latestRisk());
    assertNull(view.latestDecision());
  }

  @Test
  void latestArtifactsNullWhenLatestRunHasNone() {
    Change change = seedChange("PR-42");
    AnalysisRun older = seedRun(change, "sha-old", "2026-08-15T10:00:00Z");
    RiskAssessment olderRisk = seedRisk(older.getId());
    seedDecision(older.getId(), olderRisk.getId());
    seedRun(change, "sha-new", "2026-08-15T12:00:00Z");

    ChangeAnalysisView view = service.handle(
        new GetChangeQuery(change.getId()), tenantId, engineer);

    assertNull(view.latestRisk());
    assertNull(view.latestDecision());
    assertEquals("sha-new", view.runs().get(view.runs().size() - 1)
        .getCodeSnapshot().commitSha());
  }

  @Test
  void systemWorkerRoleRejectedAndTenantAdminAllowed() {
    Change change = seedChange("PR-42");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetChangeQuery(change.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());

    ChangeAnalysisView adminView = service.handle(
        new GetChangeQuery(change.getId()), tenantId,
        new Actor("admin-1", Actor.Role.TENANT_ADMIN));
    assertEquals(change.getId(), adminView.change().getId());
  }

  @Test
  void runsPersistOrderedOldestFirst() {
    Change change = seedChange("PR-42");
    AnalysisRun first = seedRun(change, "sha-1", "2026-08-15T08:00:00Z");
    AnalysisRun second = seedRun(change, "sha-2", "2026-08-15T10:00:00Z");
    AnalysisRun third = seedRun(change, "sha-3", "2026-08-15T12:00:00Z");

    ChangeAnalysisView view = service.handle(
        new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(List.of(first.getId(), second.getId(), third.getId()),
        view.runs().stream().map(AnalysisRun::getId).toList());
  }

  private Change seedChange(String providerChangeId) {
    Change change = new Change(
        ChangeId.generate(), repositoryId, providerChangeId,
        "Add feature", "desc", "alice", "feature", "main", "sha-new",
        Instant.parse("2026-08-15T12:00:00Z"));
    return changeRepository.save(tenantId, change);
  }

  private AnalysisRun seedRun(Change change, String commitSha, String createdAt) {
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.parse(createdAt));
    return analysisRunRepository.save(tenantId, run);
  }

  private RiskAssessment seedRisk(AnalysisRunId runId) {
    RiskAssessment assessment = new RiskAssessment(
        RiskAssessmentId.generate(), runId, RiskScore.of(30), RiskLevel.LOW,
        List.of(), EvidenceState.EVIDENCE_AVAILABLE, "1.0.0",
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
        .reasons(List.of())
        .requiredActions(List.of())
        .generatedAt(Instant.parse("2026-08-15T12:05:00Z"))
        .build();
    return decisionRecordRepository.save(tenantId, decision);
  }
}
