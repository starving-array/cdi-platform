package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.application.port.out.SystemContextPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.AnalysisCompleted;
import com.cdi.common.domain.event.AnalysisFailed;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.event.RiskAssessed;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.ServiceDependency;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-03 AnalyzeChange. Uses fake ports only at the application
 * boundary; no Testcontainers, no Spring context, no persistence is started.
 * The {@link DeterministicRiskEngine} is real so asserted scores/levels reflect
 * the documented formula (risk-engine.md §5).
 */
class AnalyzeChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();
  private final String commitSha = "abc123sha";

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakeSourceControlPort sourceControlPort;
  private FakeSystemContextPort systemContextPort;
  private FakeEvidenceSearchPort evidenceSearchPort;
  private FakeJobQueuePort jobQueuePort;
  private FakeDomainEventPublisher eventPublisher;
  private AnalyzeChangeHandler handler;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    riskAssessmentRepository = new FakeRiskAssessmentRepository();
    sourceControlPort = new FakeSourceControlPort();
    systemContextPort = new FakeSystemContextPort();
    evidenceSearchPort = new FakeEvidenceSearchPort();
    jobQueuePort = new FakeJobQueuePort();
    eventPublisher = new FakeDomainEventPublisher();
    com.cdi.application.analysis.CodeContextAssembler mockAssembler = org.mockito.Mockito.mock(com.cdi.application.analysis.CodeContextAssembler.class);
    org.mockito.Mockito.when(mockAssembler.assemble(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2)))
        .thenReturn(new com.cdi.analysis.domain.CodeContext("sha", java.util.List.of()));
    handler = new AnalyzeChangeHandler(
        changeRepository, analysisRunRepository, riskAssessmentRepository,
        sourceControlPort, systemContextPort, evidenceSearchPort,
        jobQueuePort, eventPublisher, new DeterministicRiskEngine(), mockAssembler, CLOCK);
  }

  @Test
  void happyPathAssessesRiskAndEnqueuesInvestigationForHighRiskTierZero() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 10, 2, FileDiff.ChangeType.MODIFIED));
    systemContextPort.criticality = CriticalityTier.TIER_0;
    evidenceSearchPort.records = List.of(newIncidentRecord());

    handler.handle(new AnalyzeChangeCommand(runId));

    AnalysisRun saved = analysisRunRepository.byId.get(runId.value());
    assertEquals(AnalysisRun.Status.RUNNING, saved.getStatus());
    // completedAt is no longer set here

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    // base 10 + Tier-0 30 + incident 20 = 60 -> HIGH (>=60)
    assertEquals(60, assessment.getScore().value());
    assertEquals(com.cdi.risk.domain.RiskLevel.HIGH, assessment.getLevel());
    assertEquals(EvidenceState.EVIDENCE_AVAILABLE, assessment.getEvidenceState());

    // High-risk / Tier-0 routes to the investigation stage, NOT straight to
    // the decision stage (analysis-workflow.md §5 / application-layer.md §13).
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("InvestigateRiskCommand", jobQueuePort.commandNames.get(0));
    assertEquals(runId, ((InvestigateRiskCommand) jobQueuePort.payloads.get(0)).analysisRunId());
    assertEquals(new IdempotencyKey(tenantId.value() + ":" + runId.value()),
        jobQueuePort.keys.get(0));

    assertEquals(2, eventPublisher.events.size());
    assertTrue(eventPublisher.events.stream().anyMatch(e -> e instanceof RiskAssessed));
    assertTrue(eventPublisher.events.stream().anyMatch(e -> e instanceof AnalysisCompleted));
    assertFalse(eventPublisher.events.stream().anyMatch(e -> e instanceof AnalysisFailed));
  }

  @Test
  void lowRiskNonTierZeroRunEnqueuesPolicyEvaluationDirectly() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    systemContextPort.criticality = CriticalityTier.TIER_3;
    evidenceSearchPort.records = List.of();

    handler.handle(new AnalyzeChangeCommand(runId));

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    assertEquals(com.cdi.risk.domain.RiskLevel.LOW, assessment.getLevel());

    // Deterministic, low-risk, non-Tier-0 skips investigation and goes
    // straight to policy evaluation (the policy-only path).
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("EvaluatePolicyCommand", jobQueuePort.commandNames.get(0));
    assertEquals(runId,
        ((com.cdi.application.port.in.EvaluatePolicyCommand) jobQueuePort.payloads.get(0))
            .analysisRunId());
  }

  @Test
  void configAndDatabaseMigrationFilesRaiseRisk() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("db/migration/V42__alter.sql", 20, 5, FileDiff.ChangeType.ADDED),
        new FileDiff("config/application.yml", 3, 1, FileDiff.ChangeType.MODIFIED));
    systemContextPort.criticality = CriticalityTier.TIER_3;
    evidenceSearchPort.records = List.of();

    handler.handle(new AnalyzeChangeCommand(runId));

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    // base 10 + DB 25 + config 10 = 45 -> MEDIUM (Tier-3 and <20 files add nothing)
    assertEquals(45, assessment.getScore().value());
    assertTrue(assessment.getFactors().stream()
        .anyMatch(f -> f.type() == RiskFactorType.DATABASE_SCHEMA_CHANGE));
    assertTrue(assessment.getFactors().stream()
        .anyMatch(f -> f.type() == RiskFactorType.CONFIGURATION_CHANGE));
    assertEquals(EvidenceState.NO_RELEVANT_EVIDENCE, assessment.getEvidenceState());
  }

  @Test
  void lostClaimIsAnIdempotentNoOp() {
    openChange();
    AnalysisRun run = queuedRun();
    run.start(); // claim returns false because run is no longer QUEUED

    handler.handle(new AnalyzeChangeCommand(runId));

    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
    assertEquals(AnalysisRun.Status.RUNNING, analysisRunRepository.byId.get(runId.value()).getStatus());
  }

  @Test
  void missingRunIsANoOp() {
    handler.handle(new AnalyzeChangeCommand(AnalysisRunId.generate()));
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void missingChangeFailsTheRun() {
    queuedRun();

    handler.handle(new AnalyzeChangeCommand(runId));

    AnalysisRun saved = analysisRunRepository.byId.get(runId.value());
    assertEquals(AnalysisRun.Status.FAILED, saved.getStatus());
    assertEquals("change-not-found", saved.getFailureInfo().orElseThrow().failureCode());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.stream().anyMatch(e -> e instanceof AnalysisFailed));
  }

  @Test
  void sourceControlFailureFailsTheRun() {
    openChange();
    queuedRun();
    sourceControlPort.failGetDiff = true;

    handler.handle(new AnalyzeChangeCommand(runId));

    AnalysisRun saved = analysisRunRepository.byId.get(runId.value());
    assertEquals(AnalysisRun.Status.FAILED, saved.getStatus());
    assertEquals(com.cdi.analysis.domain.AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE,
        saved.getFailureInfo().orElseThrow().category());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    AnalysisFailed event = (AnalysisFailed) eventPublisher.events.stream()
        .filter(e -> e instanceof AnalysisFailed).findFirst().orElseThrow();
    assertEquals(com.cdi.analysis.domain.AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE,
        event.failureCategory());
  }

  @Test
  void systemContextFailureDegradesToConservativeHighRiskTier() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    systemContextPort.failCriticality = true;

    handler.handle(new AnalyzeChangeCommand(runId));

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    // base 10 + degraded Tier-0 30 = 40 -> MEDIUM (treated conservatively high-risk)
    assertEquals(40, assessment.getScore().value());
    assertEquals(AnalysisRun.Status.RUNNING,
        analysisRunRepository.byId.get(runId.value()).getStatus());
    assertFalse(eventPublisher.events.stream().anyMatch(e -> e instanceof AnalysisFailed));
  }

  @Test
  void evidenceFailureDegradesToRetrievalFailedState() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    evidenceSearchPort.failSearch = true;

    handler.handle(new AnalyzeChangeCommand(runId));

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    // base 10 only, no incident factor (Tier-3)
    assertEquals(10, assessment.getScore().value());
    assertEquals(EvidenceState.EVIDENCE_RETRIEVAL_FAILED, assessment.getEvidenceState());
    assertEquals(AnalysisRun.Status.RUNNING,
        analysisRunRepository.byId.get(runId.value()).getStatus());
  }

  @Test
  void queueFailurePropagatesFromFinalStep() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    jobQueuePort.failEnqueue = true;

    try {
      handler.handle(new AnalyzeChangeCommand(runId));
    } catch (PortException e) {
      assertEquals(PortType.JOB_QUEUE, e.getPort());
      return;
    }
    throw new AssertionError("expected PortException to propagate");
  }

  @Test
  void downstreamDependencyCountIsZeroWhenUnresolvable() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));

    handler.handle(new AnalyzeChangeCommand(runId));

    // 6+ downstream dependencies can never be reached in P0, so no
    // HIGH_DEPENDENCY_IMPACT factor is ever produced by this handler.
    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    assertFalse(assessment.getFactors().stream()
        .anyMatch(f -> f.type() == RiskFactorType.HIGH_DEPENDENCY_IMPACT));
    assertEquals(10, assessment.getScore().value());
  }

  @Test
  void riskPersistenceFailurePropagates() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    riskAssessmentRepository.failSave = true;

    try {
      handler.handle(new AnalyzeChangeCommand(runId));
      throw new AssertionError("expected PortException to propagate");
    } catch (PortException e) {
      assertEquals(PortType.RISK_ASSESSMENT_REPOSITORY, e.getPort());
    }
  }



  @Test
  void runOfAnotherTenantCannotResolveItsChange() {
    TenantId otherTenant = TenantId.generate();
    openChange(); // registered under the test tenant
    AnalysisRun run = queuedRun();
    analysisRunRepository.runTenants.put(runId.value(), otherTenant);

    handler.handle(new AnalyzeChangeCommand(runId));

    AnalysisRun saved = analysisRunRepository.byId.get(runId.value());
    assertEquals(AnalysisRun.Status.FAILED, saved.getStatus());
    assertEquals("change-not-found", saved.getFailureInfo().orElseThrow().failureCode());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void alreadyCompletedRunIsAnIdempotentNoOp() {
    openChange();
    AnalysisRun run = completedRun("abc123sha");
    analysisRunRepository.byId.put(runId.value(), run);

    handler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(AnalysisRun.Status.COMPLETED,
        analysisRunRepository.byId.get(runId.value()).getStatus());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void runningRunIsAnIdempotentNoOp() {
    openChange();
    AnalysisRun run = runningRun("abc123sha");
    analysisRunRepository.byId.put(runId.value(), run);

    handler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(AnalysisRun.Status.RUNNING,
        analysisRunRepository.byId.get(runId.value()).getStatus());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void failedRunIsNotSilentlyRestarted() {
    openChange();
    AnalysisRun run = failedRun("abc123sha");
    analysisRunRepository.byId.put(runId.value(), run);

    handler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(AnalysisRun.Status.FAILED,
        analysisRunRepository.byId.get(runId.value()).getStatus());
    assertTrue(analysisRunRepository.byId.get(runId.value()).getFailureInfo().isPresent());
    assertTrue(riskAssessmentRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void analyzeNeverCreatesAnotherAnalysisRun() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));

    handler.handle(new AnalyzeChangeCommand(runId));

    // Only the pre-existing run may ever be present — AnalyzeChange consumes,
    // it never inserts (REQUEST-ANALYSIS owns run creation).
    assertEquals(1, analysisRunRepository.byId.size());
    assertTrue(analysisRunRepository.byId.containsKey(runId.value()));
    assertEquals(AnalysisRun.Status.RUNNING,
        analysisRunRepository.byId.get(runId.value()).getStatus());
  }

  @Test
  void fileDiffFieldsDriveRiskInput() {
    openChange();
    queuedRun();
    // 25 files plus one SQL migration -> HIGH_CHANGE_SIZE + DATABASE_SCHEMA_CHANGE
    List<FileDiff> diff = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      diff.add(new FileDiff("src/Gen" + i + ".java", 10, 5, FileDiff.ChangeType.MODIFIED));
    }
    diff.add(new FileDiff("db/migration/V50__x.sql", 1, 0, FileDiff.ChangeType.ADDED));
    sourceControlPort.diff = diff;

    handler.handle(new AnalyzeChangeCommand(runId));

    RiskAssessment assessment = riskAssessmentRepository.saved.get(0);
    // base 10 + high-change-size 10 + db migration 25 = 45 -> MEDIUM
    assertEquals(45, assessment.getScore().value());
    assertEquals(com.cdi.risk.domain.RiskLevel.MEDIUM, assessment.getLevel());
    assertTrue(assessment.getFactors().stream()
        .anyMatch(f -> f.type() == RiskFactorType.HIGH_CHANGE_SIZE));
    assertTrue(assessment.getFactors().stream()
        .anyMatch(f -> f.type() == RiskFactorType.DATABASE_SCHEMA_CHANGE));
  }

  @Test
  void diffPathsAreSentToSystemContextAndEvidencePorts() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED),
        new FileDiff("config/app.yml", 2, 0, FileDiff.ChangeType.MODIFIED));
    List<String> paths = List.of("src/App.java", "config/app.yml");
    systemContextPort.recordedPaths = new java.util.ArrayList<>();
    evidenceSearchPort.recordedPaths = new java.util.ArrayList<>();

    handler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(paths, systemContextPort.recordedPaths);
    assertEquals(paths, evidenceSearchPort.recordedPaths);
  }

  private Change openChange() {
    Change change = new Change(
        changeId, repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", commitSha, NOW);
    changeRepository.byId.put(tenantId.value() + "|" + changeId.value(), change);
    return change;
  }

  private AnalysisRun queuedRun() {
    AnalysisRun run = new AnalysisRun(
        runId, changeId, new CodeSnapshot(commitSha, "feature-x"), NOW);
    analysisRunRepository.byId.put(runId.value(), run);
    return run;
  }

  private AnalysisRun runningRun(String sha) {
    AnalysisRun run = new AnalysisRun(
        runId, changeId, new CodeSnapshot(sha, "feature-x"), NOW);
    run.start();
    analysisRunRepository.byId.put(runId.value(), run);
    return run;
  }

  private AnalysisRun completedRun(String sha) {
    AnalysisRun run = new AnalysisRun(
        runId, changeId, new CodeSnapshot(sha, "feature-x"), NOW);
    run.start();
    run.complete(NOW.plusSeconds(30));
    analysisRunRepository.byId.put(runId.value(), run);
    return run;
  }

  private AnalysisRun failedRun(String sha) {
    AnalysisRun run = new AnalysisRun(
        runId, changeId, new CodeSnapshot(sha, "feature-x"), NOW);
    run.start();
    run.fail(new com.cdi.analysis.domain.AnalysisFailure(
        com.cdi.analysis.domain.AnalysisFailure.FailureCategory.ANALYSIS_FAILED,
        "prev", NOW.plusSeconds(5)));
    analysisRunRepository.byId.put(runId.value(), run);
    return run;
  }

  private EvidenceRecord newIncidentRecord() {
    return EvidenceRecord.builder()
        .tenantId(tenantId)
        .analysisRunId(runId)
        .source(new EvidenceSource(SourceType.INCIDENT, "INC-100"))
        .origin(EvidenceOrigin.RETRIEVED)
        .title("Past incident on this path")
        .build();
  }

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<String, Change> byId = new HashMap<>();

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return byId.values().stream().findFirst();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return Optional.ofNullable(byId.get(key(tenantId, changeId)));
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      byId.put(key(tenantId, change.getId()), change);
      return change;
    }

    private static String key(TenantId tenantId, ChangeId changeId) {
      return tenantId.value() + "|" + changeId.value();
    }
  }

  private class FakeAnalysisRunRepository implements AnalysisRunRepository {
    final Map<java.util.UUID, AnalysisRun> byId = new HashMap<>();
    final Map<java.util.UUID, TenantId> runTenants = new HashMap<>();
    boolean failSave;

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
              runTenants.getOrDefault(analysisRunId.value(), tenantId), run));
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      AnalysisRun run = byId.get(analysisRunId.value());
      if (run == null || run.getStatus() != AnalysisRun.Status.QUEUED) {
        return false;
      }
      return true;
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      if (failSave) {
        throw new PortException(PortType.ANALYSIS_RUN_REPOSITORY, false, "analysis run db down");
      }
      byId.put(run.getId().value(), run);
      return run;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(
        TenantId tenantId, ChangeId changeId) {
      return List.of();
    }
  }

  private static class FakeRiskAssessmentRepository implements RiskAssessmentRepository {
    final List<RiskAssessment> saved = new ArrayList<>();
    boolean failSave;

    @Override
    public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
      if (failSave) {
        throw new PortException(PortType.RISK_ASSESSMENT_REPOSITORY, false, "risk db down");
      }
      saved.add(riskAssessment);
      return riskAssessment;
    }

    @Override
    public java.util.Optional<RiskAssessment> findByAnalysisRunId(
        TenantId tenantId, com.cdi.common.domain.id.AnalysisRunId analysisRunId) {
      return saved.stream()
          .filter(a -> a.getAnalysisRunId().equals(analysisRunId))
          .findFirst();
    }
  }

  private class FakeSourceControlPort implements SourceControlPort {
    @Override public java.util.List<String> listFiles(com.cdi.common.domain.id.TenantId t, com.cdi.common.domain.id.RepositoryId r, String c) { return java.util.List.of(); }
    List<FileDiff> diff = List.of();
    boolean failGetDiff;

    @Override
    public com.cdi.application.port.out.ChangeMetadata getChangeMetadata(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return new com.cdi.application.port.out.ChangeMetadata(
          providerChangeId, "t", "", "a", "feature-x", "main", commitSha);
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      if (failGetDiff) {
        throw new PortException(PortType.SOURCE_CONTROL, true, "scm 5xx");
      }
      return diff;
    }

    @Override
    public byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path, String commitSha) {
      return new byte[0];
    }

    @Override
    public void publishStatusCheck(
        TenantId tenantId, RepositoryId repositoryId, String commitSha,
        com.cdi.decision.domain.DecisionOutcome outcome,
        List<com.cdi.decision.domain.DecisionReason> reasons, String detailsUrl) {
      // not used by AnalyzeChange
    }
  }

  private static class FakeSystemContextPort implements SystemContextPort {
    CriticalityTier criticality = CriticalityTier.TIER_3;
    boolean failCriticality;
    List<String> recordedPaths;

    @Override
    public CriticalityTier getServiceCriticality(
        TenantId tenantId, RepositoryId repositoryId, List<String> filePaths) {
      if (failCriticality) {
        throw new PortException(PortType.SYSTEM_CONTEXT, true, "catalog down");
      }
      recordedPaths = filePaths;
      return criticality;
    }

    @Override
    public List<ServiceDependency> getDependencies(TenantId tenantId, ServiceId serviceId) {
      return List.of();
    }
  }

  private static class FakeEvidenceSearchPort implements EvidenceSearchPort {
    List<EvidenceRecord> records = List.of();
    boolean failSearch;
    List<String> recordedPaths;

    @Override
    public List<EvidenceRecord> searchSimilarChanges(
        TenantId tenantId, List<String> filePaths, int limit) {
      if (failSearch) {
        throw new PortException(PortType.EVIDENCE_SEARCH, false, "vector db down");
      }
      recordedPaths = filePaths;
      return records;
    }

    @Override
    public List<EvidenceRecord> searchIncidents(
        TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit) {
      return List.of();
    }

    @Override
    public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
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
      // not used by AnalyzeChange
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

