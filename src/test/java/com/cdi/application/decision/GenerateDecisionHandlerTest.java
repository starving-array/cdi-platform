package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-06 final decision delivery — the downstream
 * status-publication stage after UC-05 policy evaluation. Uses fake ports only;
 * no Testcontainers, no Spring context, no persistence, no PolicyEngine.
 */
class GenerateDecisionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final String COMMIT_SHA = "abc123sha";

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();

  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeChangeRepository changeRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private FakeSourceControlPort sourceControlPort;
  private GenerateDecisionHandler handler;

  @BeforeEach
  void setUp() {
    analysisRunRepository = new FakeAnalysisRunRepository(tenantId);
    changeRepository = new FakeChangeRepository();
    decisionRecordRepository = new FakeDecisionRecordRepository(tenantId);
    sourceControlPort = new FakeSourceControlPort();
    handler = new GenerateDecisionHandler(
        analysisRunRepository,
        changeRepository,
        decisionRecordRepository,
        sourceControlPort,
        java.time.Clock.fixed(NOW, java.time.ZoneId.of("UTC")),
        "http://localhost:5173");
  }

  @Test
  void completedRunWithDecisionPublishesStatusCheck() {
    seedCompletedRunWithDecision();

    handler.handle(new GenerateDecisionCommand(runId));

    assertEquals(1, sourceControlPort.statusChecks.size());
    StatusCall call = sourceControlPort.statusChecks.get(0);
    assertEquals(tenantId, call.tenantId);
    assertEquals(repositoryId, call.repositoryId);
    assertEquals(COMMIT_SHA, call.commitSha);
    assertEquals(DecisionOutcome.BLOCK, call.outcome);
    assertEquals(List.of("cap"), call.reasons.stream().map(DecisionReason::ruleId).toList());
    assertEquals("http://localhost:5173/runs/" + runId.value() + "/decision", call.detailsUrl);
  }

  @Test
  void missingAnalysisRaisesTypedError() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new GenerateDecisionCommand(AnalysisRunId.generate())));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("analysis-not-found", ex.getDetails().get("reason"));
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  @Test
  void nonCompletedAnalysisRaisesTypedError() {
    AnalysisRun run = queuedRun();
    analysisRunRepository.byId.put(runId.value(), run);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new GenerateDecisionCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("analysis-not-running", ex.getDetails().get("reason"));
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  @Test
  void missingPersistedDecisionRaisesTypedError() {
    AnalysisRun run = completedRun();
    analysisRunRepository.byId.put(runId.value(), run);
    changeRepository.byId.put(
        tenantId.value() + "|" + changeId.value(), openChange());

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new GenerateDecisionCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("policy-evaluation-missing", ex.getDetails().get("reason"));
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  @Test
  void missingChangeRaisesTypedError() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenantId).analysisRunId(runId)
        .riskAssessmentId(RiskAssessmentId.generate()).policyId(PolicyId.generate())
        .policyVersion("v1").outcome(DecisionOutcome.BLOCK)
        .reasons(List.of(new DecisionReason("Blocked", "cap", List.of())))
        .requiredActions(List.of()).generatedAt(NOW).build();
    decisionRecordRepository.byRun.put(runId.value(), decision);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new GenerateDecisionCommand(runId)));
    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
    assertEquals("change-not-found", ex.getDetails().get("reason"));
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  @Test
  void rejectsStatusPublicationForAnotherTenant() {
    TenantId otherTenant = TenantId.generate();
    AnalysisRun otherRun = completedRun(otherTenant, AnalysisRunId.generate());
    analysisRunRepository.byId.put(otherRun.getId().value(), otherRun);
    analysisRunRepository.runTenants.put(otherRun.getId().value(), otherTenant);
    changeRepository.byId.put(
        otherTenant.value() + "|" + otherRun.getChangeId().value(), openChangeFor(otherTenant));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new GenerateDecisionCommand(otherRun.getId())));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  @Test
  void repeatedInvocationPostsSameDeterministicStatus() {
    seedCompletedRunWithDecision();

    handler.handle(new GenerateDecisionCommand(runId));
    handler.handle(new GenerateDecisionCommand(runId));

    assertEquals(2, sourceControlPort.statusChecks.size());
    StatusCall first = sourceControlPort.statusChecks.get(0);
    StatusCall second = sourceControlPort.statusChecks.get(1);
    assertEquals(first.commitSha, second.commitSha);
    assertEquals(first.outcome, second.outcome);
    assertEquals(first.reasons, second.reasons);
    assertEquals(first.detailsUrl, second.detailsUrl);
    assertEquals(0, decisionRecordRepository.saved.size());
    assertEquals(1, decisionRecordRepository.byRun.size());
  }

  @Test
  void statusPortFailureSurfacesAndDoesNotEraseDecision() {
    seedCompletedRunWithDecision();
    sourceControlPort.failStatusCheck = true;

    assertThrows(PortException.class,
        () -> handler.handle(new GenerateDecisionCommand(runId)));
    assertEquals(1, decisionRecordRepository.byRun.size());
  }

  // ---- helpers ----

  private void seedCompletedRunWithDecision() {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    changeRepository.byId.put(
        tenantId.value() + "|" + changeId.value(), openChange());
    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenantId)
        .analysisRunId(runId)
        .riskAssessmentId(RiskAssessmentId.generate())
        .policyId(PolicyId.generate())
        .policyVersion("v1")
        .outcome(DecisionOutcome.BLOCK)
        .reasons(List.of(new DecisionReason("Blocked", "cap", List.of())))
        .requiredActions(List.of())
        .generatedAt(NOW)
        .build();
    decisionRecordRepository.byRun.put(runId.value(), decision);
  }

  private AnalysisRun completedRun(TenantId tenant, AnalysisRunId id) {
    AnalysisRun run = queuedRun(tenant, id);
    run.start();
    run.complete(NOW.plusSeconds(30));
    return run;
  }

  private AnalysisRun completedRun() {
    return completedRun(tenantId, runId);
  }

  private AnalysisRun queuedRun(TenantId tenant, AnalysisRunId id) {
    return new AnalysisRun(
        id, changeId, new CodeSnapshot(COMMIT_SHA, "feature-x"), NOW);
  }

  private AnalysisRun queuedRun() {
    return queuedRun(tenantId, runId);
  }

  private Change openChange() {
    return openChangeFor(tenantId);
  }

  private Change openChangeFor(TenantId tenant) {
    return new Change(
        changeId, repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", COMMIT_SHA, NOW);
  }

  // ---- fakes ----

  private static class FakeAnalysisRunRepository implements AnalysisRunRepository {
    private final TenantId defaultTenant;
    final Map<UUID, AnalysisRun> byId = new HashMap<>();
    final Map<UUID, TenantId> runTenants = new HashMap<>();

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
          .map(run -> new AnalysisRunContext(
              runTenants.getOrDefault(analysisRunId.value(), defaultTenant), run));
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

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<String, Change> byId = new HashMap<>();

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return byId.values().stream().findFirst();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return Optional.ofNullable(byId.get(tenantId.value() + "|" + changeId.value()));
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      byId.put(tenantId.value() + "|" + change.getId().value(), change);
      return change;
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

  private static class FakeSourceControlPort implements SourceControlPort {

    @Override
    public java.util.List<String> listFiles(com.cdi.common.domain.id.TenantId tenantId, com.cdi.common.domain.id.RepositoryId repositoryId, String commitSha) {
        return java.util.List.of();
    }

    final List<StatusCall> statusChecks = new ArrayList<>();
    boolean failStatusCheck;

    @Override
    public ChangeMetadata getChangeMetadata(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return new ChangeMetadata(providerChangeId, "t", "", "a", "feature-x", "main", COMMIT_SHA);
    }

    @Override
    public List<com.cdi.analysis.domain.FileDiff> getDiff(
        TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      return List.of();
    }

    @Override
    public byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path, String commitSha) {
      return new byte[0];
    }

    @Override
    public void publishStatusCheck(
        TenantId tenantId, RepositoryId repositoryId, String commitSha,
        DecisionOutcome outcome, List<DecisionReason> reasons, String detailsUrl) {
      if (failStatusCheck) {
        throw new PortException(PortType.SOURCE_CONTROL, true, "scm 5xx");
      }
      statusChecks.add(new StatusCall(tenantId, repositoryId, commitSha, outcome, reasons, detailsUrl));
    }
  }

  private record StatusCall(
      TenantId tenantId,
      RepositoryId repositoryId,
      String commitSha,
      DecisionOutcome outcome,
      List<DecisionReason> reasons,
      String detailsUrl) {}
}

