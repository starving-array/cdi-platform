package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetChangeBySourceQuery;
import com.cdi.application.port.in.GetChangeQuery;
import com.cdi.application.port.out.AnalysisRunContext;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-11 GetChange. Uses fake ports only at the application
 * boundary; no Testcontainers, no Spring context, no persistence is started.
 * Verifies both lookup forms, tenant isolation, ENGINEER role enforcement,
 * deterministic run ordering, and the empty-history contract.
 */
class GetChangeQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final TenantId tenantId = TenantId.generate();

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private GetChangeQueryService service;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    riskAssessmentRepository = new FakeRiskAssessmentRepository();
    decisionRecordRepository = new FakeDecisionRecordRepository();
    service = new GetChangeQueryService(
        changeRepository, analysisRunRepository, riskAssessmentRepository,
        decisionRecordRepository);
  }

  @Test
  void byIdReturnsFullViewWithHistoryAndLatestArtifacts() {
    Change change = changeRepository.seed(tenantId, "PR-42");
    AnalysisRun oldRun = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-old", "main"), Instant.parse("2026-08-15T10:00:00Z"));
    AnalysisRun newRun = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));
    riskAssessmentRepository.seed(tenantId, oldRun.getId());
    RiskAssessment latestRisk = riskAssessmentRepository.seed(tenantId, newRun.getId());
    DecisionRecord latestDecision = decisionRecordRepository.seed(
        tenantId, newRun.getId(), latestRisk.getId());

    ChangeAnalysisView view = service.handle(new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(change.getId(), view.change().getId());
    assertEquals(2, view.runs().size());
    assertEquals(oldRun.getId(), view.runs().get(0).getId());
    assertEquals(newRun.getId(), view.runs().get(1).getId());
    assertEquals(latestRisk.getId(), view.latestRisk().getId());
    assertEquals(latestDecision.getId(), view.latestDecision().getId());
  }

  @Test
  void byIdWithNoRunsReturnsEmptyHistoryAndNullArtifacts() {
    Change change = changeRepository.seed(tenantId, "PR-42");

    ChangeAnalysisView view = service.handle(new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(change.getId(), view.change().getId());
    assertTrue(view.runs().isEmpty());
    assertNull(view.latestRisk());
    assertNull(view.latestDecision());
  }

  @Test
  void bySourceReferenceReturnsView() {
    Change change = changeRepository.seed(tenantId, "PR-42");
    analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    ChangeAnalysisView view = service.handle(
        new GetChangeBySourceQuery(tenantId, change.getRepositoryId(), "PR-42"), engineer);

    assertEquals(change.getId(), view.change().getId());
    assertEquals(1, view.runs().size());
  }

  @Test
  void byIdChangeNotFoundRaisesChangeNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetChangeQuery(ChangeId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void bySourceChangeNotFoundRaisesChangeNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetChangeBySourceQuery(tenantId, RepositoryId.generate(), "PR-999"), engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantLookupIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    changeRepository.seed(tenantB, "PR-42");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetChangeBySourceQuery(tenantId, RepositoryId.generate(), "PR-42"), engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void byIdIsScopedToContextTenant() {
    Change change = changeRepository.seed(tenantId, "PR-42");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetChangeQuery(change.getId()), TenantId.generate(), engineer));

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
  }

  @Test
  void nonEngineerRoleRejected() {
    Change change = changeRepository.seed(tenantId, "PR-42");

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new GetChangeQuery(change.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());

    ApplicationException adminEx = assertThrows(ApplicationException.class,
        () -> service.handle(new GetChangeQuery(change.getId()), tenantId,
            new Actor("admin-1", Actor.Role.TENANT_ADMIN)));
    assertEquals(ApplicationError.UNAUTHORIZED, adminEx.getError());
  }

  @Test
  void runsOrderedOldestFirstAndLatestIsNewest() {
    Change change = changeRepository.seed(tenantId, "PR-42");
    AnalysisRun oldest = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-1", "main"), Instant.parse("2026-08-15T08:00:00Z"));
    AnalysisRun middle = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-2", "main"), Instant.parse("2026-08-15T10:00:00Z"));
    AnalysisRun newest = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-3", "main"), Instant.parse("2026-08-15T12:00:00Z"));

    ChangeAnalysisView view = service.handle(new GetChangeQuery(change.getId()), tenantId, engineer);

    assertEquals(List.of(oldest.getId(), middle.getId(), newest.getId()),
        view.runs().stream().map(AnalysisRun::getId).toList());
    assertEquals(newest.getId(), view.runs().get(view.runs().size() - 1).getId());
  }

  @Test
  void latestArtifactsResolvedFromMostRecentRunOnly() {
    Change change = changeRepository.seed(tenantId, "PR-42");
    AnalysisRun older = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-old", "main"), Instant.parse("2026-08-15T10:00:00Z"));
    AnalysisRun newest = analysisRunRepository.seed(tenantId, change,
        new CodeSnapshot("sha-new", "main"), Instant.parse("2026-08-15T12:00:00Z"));
    RiskAssessment olderRisk = riskAssessmentRepository.seed(tenantId, older.getId());
    decisionRecordRepository.seed(tenantId, older.getId(), olderRisk.getId());

    // The most recent run has no risk/decision yet — the view must reflect the
    // latest run's absence, not fall back to an older run's artifacts.
    ChangeAnalysisView view = service.handle(new GetChangeQuery(change.getId()), tenantId, engineer);

    assertNull(view.latestRisk());
    assertNull(view.latestDecision());
    assertEquals(newest.getId(), view.runs().get(view.runs().size() - 1).getId());
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<UUID, List<Change>> byTenant = new HashMap<>();

    Change seed(TenantId tenantId, String providerChangeId) {
      Change change = new Change(
          ChangeId.generate(), RepositoryId.generate(), providerChangeId,
          "Add feature", "desc", "alice", "feature", "main", "sha-new",
          Instant.parse("2026-08-15T12:00:00Z"));
      byTenant.computeIfAbsent(tenantId.value(), k -> new ArrayList<>()).add(change);
      return change;
    }

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return byTenant.getOrDefault(tenantId.value(), List.of()).stream()
          .filter(c -> c.getRepositoryId().equals(repositoryId))
          .filter(c -> c.getProviderChangeId().equals(providerChangeId))
          .findFirst();
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
