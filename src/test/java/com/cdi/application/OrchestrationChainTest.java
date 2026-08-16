package com.cdi.application;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.analysis.AnalyzeChangeHandler;
import com.cdi.application.analysis.InvestigateRiskHandler;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.decision.EvaluatePolicyHandler;
import com.cdi.application.decision.GenerateDecisionHandler;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.AgentContext;
import com.cdi.application.port.out.AgentInvestigationRepository;
import com.cdi.application.port.out.AgentPort;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.application.port.out.InvestigationFindings;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.application.port.out.SystemContextPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DecisionGenerated;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.investigation.domain.AgentInvestigation;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyEngine;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused orchestration tests for the corrected UC-02 → UC-06 command chain.
 * All four handlers are wired to one set of fake ports and driven stage by
 * stage, following the actual enqueued commands (no dispatcher exists in P0).
 * The real {@link DeterministicRiskEngine} and {@link PolicyEngine} are used so
 * the chain reflects the documented formulas; only IO is faked.
 *
 * <p>Command graph under test (application-layer.md §5/§6/§13):
 * <pre>
 * AnalyzeChangeCommand
 *     +--> InvestigateRiskCommand --> EvaluatePolicyCommand --> GenerateDecisionCommand
 *     +--> EvaluatePolicyCommand --> GenerateDecisionCommand
 * </pre>
 */
class OrchestrationChainTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String COMMIT_SHA = "abc123sha";

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();

  private FakeChangeRepository changeRepository;
  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakePolicyRepository policyRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private FakeAgentInvestigationRepository agentInvestigationRepository;
  private FakeSourceControlPort sourceControlPort;
  private FakeSystemContextPort systemContextPort;
  private FakeEvidenceSearchPort evidenceSearchPort;
  private FakeAgentPort agentPort;
  private FakeJobQueuePort jobQueuePort;
  private FakeDomainEventPublisher eventPublisher;

  private AnalyzeChangeHandler analyzeChangeHandler;
  private InvestigateRiskHandler investigateRiskHandler;
  private EvaluatePolicyHandler evaluatePolicyHandler;
  private GenerateDecisionHandler generateDecisionHandler;

  @BeforeEach
  void setUp() {
    changeRepository = new FakeChangeRepository();
    analysisRunRepository = new FakeAnalysisRunRepository();
    riskAssessmentRepository = new FakeRiskAssessmentRepository();
    policyRepository = new FakePolicyRepository();
    decisionRecordRepository = new FakeDecisionRecordRepository();
    agentInvestigationRepository = new FakeAgentInvestigationRepository();
    sourceControlPort = new FakeSourceControlPort();
    systemContextPort = new FakeSystemContextPort();
    evidenceSearchPort = new FakeEvidenceSearchPort();
    agentPort = new FakeAgentPort();
    jobQueuePort = new FakeJobQueuePort();
    eventPublisher = new FakeDomainEventPublisher();

    DeterministicRiskEngine riskEngine = new DeterministicRiskEngine();
    PolicyEngine policyEngine = new PolicyEngine();

    analyzeChangeHandler = new AnalyzeChangeHandler(
        changeRepository, analysisRunRepository, riskAssessmentRepository,
        sourceControlPort, systemContextPort, evidenceSearchPort,
        jobQueuePort, eventPublisher, riskEngine, CLOCK);
    investigateRiskHandler = new InvestigateRiskHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        agentInvestigationRepository, sourceControlPort, evidenceSearchPort,
        agentPort, jobQueuePort, eventPublisher, CLOCK);
    evaluatePolicyHandler = new EvaluatePolicyHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        policyRepository, decisionRecordRepository, sourceControlPort,
        systemContextPort, policyEngine, jobQueuePort, eventPublisher, CLOCK);
    generateDecisionHandler = new GenerateDecisionHandler(
        analysisRunRepository, changeRepository, decisionRecordRepository,
        sourceControlPort);

    // A permissive tenant policy so EvaluatePolicy can always reach APPROVE.
    policyRepository.policy = approveAllPolicy();
  }

  // 1. AnalyzeChange → EvaluatePolicy for policy-only path.
  @Test
  void analyzeChange_routesLowRiskStraightToEvaluatePolicy() {
    lowRiskQueuedRun();

    analyzeChangeHandler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(List.of("EvaluatePolicyCommand"), jobQueuePort.commandNames);
    assertTrue(analysisRunRepository.byId.get(runId.value()).getStatus()
        == AnalysisRun.Status.COMPLETED);
  }

  // 2. AnalyzeChange → InvestigateRisk for investigation-required path.
  @Test
  void analyzeChange_routesTierZeroToInvestigateRisk() {
    tierZeroQueuedRun();

    analyzeChangeHandler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(List.of("InvestigateRiskCommand"), jobQueuePort.commandNames);
  }

  // 3. InvestigateRisk → EvaluatePolicy.
  @Test
  void investigateRisk_enqueuesEvaluatePolicyAfterSuccess() {
    completedRunWithRisk(RiskLevel.MEDIUM, CriticalityTier.TIER_3);
    agentPort.output = new InvestigationFindings(List.of());

    investigateRiskHandler.handle(new InvestigateRiskCommand(runId));

    assertEquals(AgentInvestigation.Status.COMPLETED,
        agentInvestigationRepository.saved.get(0).getStatus());
    assertEquals(List.of("EvaluatePolicyCommand"), jobQueuePort.commandNames);
  }

  // 4. EvaluatePolicy → GenerateDecision.
  @Test
  void evaluatePolicy_enqueuesGenerateDecisionAfterPersistingDecision() {
    completedRunWithRisk(RiskLevel.LOW, CriticalityTier.TIER_3);

    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("GenerateDecisionCommand", jobQueuePort.commandNames.get(0));
    assertTrue(eventPublisher.events.stream().anyMatch(e -> e instanceof DecisionGenerated));
  }

  // 5. Full deterministic path: AnalyzeChange → EvaluatePolicy → GenerateDecision → status.
  @Test
  void fullDeterministicPath_publishesFinalStatus() {
    lowRiskQueuedRun();
    driveChain();

    // Exactly one decision persisted, exactly one status posted, command graph
    // followed Analyze → EvaluatePolicy → GenerateDecision.
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, sourceControlPort.statusChecks.size());
    assertEquals(
        List.of("EvaluatePolicyCommand", "GenerateDecisionCommand"),
        jobQueuePort.commandNames);
  }

  // 6. Full investigation path: AnalyzeChange → InvestigateRisk → EvaluatePolicy → GenerateDecision → status.
  @Test
  void fullInvestigationPath_publishesFinalStatus() {
    tierZeroQueuedRun();
    agentPort.output = new InvestigationFindings(List.of());
    driveChain();

    assertEquals(1, agentInvestigationRepository.saved.size());
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, sourceControlPort.statusChecks.size());
    assertEquals(
        List.of("InvestigateRiskCommand", "EvaluatePolicyCommand", "GenerateDecisionCommand"),
        jobQueuePort.commandNames);
  }

  // 7. Agent investigation failure/degradation still reaches EvaluatePolicy.
  @Test
  void agentFailure_degradesToEvaluatePolicyAndStillReachesStatus() {
    tierZeroQueuedRun();
    agentPort.fail = true;
    driveChain();

    assertEquals(AgentInvestigation.Status.FAILED,
        agentInvestigationRepository.saved.get(0).getStatus());
    // Even degraded, the chain continues through EvaluatePolicy to delivery.
    assertEquals(
        List.of("InvestigateRiskCommand", "EvaluatePolicyCommand", "GenerateDecisionCommand"),
        jobQueuePort.commandNames);
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, sourceControlPort.statusChecks.size());
  }

  // 8. EvaluatePolicy creates exactly one DecisionRecord (even across the whole chain).
  @Test
  void evaluatePolicy_createsExactlyOneDecisionRecordPerRun() {
    completedRunWithRisk(RiskLevel.LOW, CriticalityTier.TIER_3);

    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));
    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, decisionRecordRepository.byRun.size());
    // Replay enqueues no second downstream command.
    assertEquals(1, jobQueuePort.commandNames.size());
  }

  // 9. GenerateDecision consumes the persisted DecisionRecord and posts the status.
  @Test
  void generateDecision_consumesPersistedDecisionAndPostsStatus() {
    completedRunWithRisk(RiskLevel.LOW, CriticalityTier.TIER_3);
    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));
    DecisionRecord persisted = decisionRecordRepository.saved.get(0);

    generateDecisionHandler.handle(new GenerateDecisionCommand(runId));

    assertEquals(1, sourceControlPort.statusChecks.size());
    FakeSourceControlPort.StatusCall call = sourceControlPort.statusChecks.get(0);
    assertEquals(persisted.getOutcome(), call.outcome);
    assertEquals(persisted.getReasons(), call.reasons);
    assertEquals(COMMIT_SHA, call.commitSha);
  }

  // 10. No duplicate GenerateDecisionCommand on repeated/idempotent EvaluatePolicy calls.
  @Test
  void repeatedEvaluatePolicy_doesNotDuplicateGenerateDecisionCommand() {
    completedRunWithRisk(RiskLevel.LOW, CriticalityTier.TIER_3);

    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));
    evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));

    // First call enqueues exactly one GenerateDecisionCommand; the idempotent
    // replay is a no-op that enqueues nothing — no duplicate downstream work.
    long generateCount = jobQueuePort.commandNames.stream()
        .filter("GenerateDecisionCommand"::equals).count();
    assertEquals(1, generateCount);
  }

  // 11. Tenant isolation across the newly connected command chain.
  @Test
  void crossTenantRun_producesNoDecisionAndNoStatus() {
    // A run owned by another tenant: AnalyzeChange cannot resolve its change
    // under that tenant, so it fails the run and enqueues nothing downstream.
    openChange();
    AnalysisRun run = queuedRun();
    analysisRunRepository.runTenants.put(runId.value(), TenantId.generate());

    analyzeChangeHandler.handle(new AnalyzeChangeCommand(runId));

    assertEquals(AnalysisRun.Status.FAILED,
        analysisRunRepository.byId.get(runId.value()).getStatus());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(sourceControlPort.statusChecks.isEmpty());

    // Driving the policy/delivery stages directly for such a run also produces
    // no decision and no status — lookups are tenant-scoped all the way down.
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(sourceControlPort.statusChecks.isEmpty());
  }

  // ---- chain driver ----

  /**
   * Drives the chain from AnalyzeChange, following the enqueued commands
   * through the command graph until GenerateDecision (the terminal stage) —
   * mirrors what an async dispatcher would do once one exists.
   */
  private void driveChain() {
    analyzeChangeHandler.handle(new AnalyzeChangeCommand(runId));
    if (endsWith("InvestigateRiskCommand")) {
      investigateRiskHandler.handle(new InvestigateRiskCommand(runId));
    }
    if (endsWith("EvaluatePolicyCommand")) {
      evaluatePolicyHandler.handle(new EvaluatePolicyCommand(runId));
    }
    if (endsWith("GenerateDecisionCommand")) {
      generateDecisionHandler.handle(new GenerateDecisionCommand(runId));
    }
  }

  private boolean endsWith(String commandName) {
    return !jobQueuePort.commandNames.isEmpty()
        && commandName.equals(jobQueuePort.commandNames.get(jobQueuePort.commandNames.size() - 1));
  }

  // ---- fixtures ----

  private Policy approveAllPolicy() {
    return new Policy(
        com.cdi.common.domain.id.PolicyId.generate(), tenantId, "Approve All", "",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        List.of(new PolicyRule("APPROVE_ALL", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(), "Approve all")),
        NOW);
  }

  private Change openChange() {
    Change change = new Change(
        changeId, repositoryId, "PR-42", "Fix bug", "", "alice",
        "feature-x", "main", COMMIT_SHA, NOW);
    changeRepository.byId.put(tenantId.value() + "|" + changeId.value(), change);
    return change;
  }

  private AnalysisRun queuedRun() {
    AnalysisRun run = new AnalysisRun(
        runId, changeId, new CodeSnapshot(COMMIT_SHA, "feature-x"), NOW);
    analysisRunRepository.byId.put(runId.value(), run);
    return run;
  }

  /** Low-risk deterministic fixture: 1 small file, Tier-3, no evidence → score 10 / LOW. */
  private void lowRiskQueuedRun() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    systemContextPort.criticality = CriticalityTier.TIER_3;
    evidenceSearchPort.records = List.of();
  }

  /** Investigation-required fixture: Tier-0 (HIGH-risk service) regardless of score. */
  private void tierZeroQueuedRun() {
    openChange();
    queuedRun();
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    systemContextPort.criticality = CriticalityTier.TIER_0;
    evidenceSearchPort.records = List.of();
  }

  /** Pre-completed run carrying a risk assessment (for direct mid-chain tests). */
  private void completedRunWithRisk(RiskLevel level, CriticalityTier tier) {
    openChange();
    AnalysisRun run = queuedRun();
    run.start();
    run.complete(NOW.plusSeconds(30));
    analysisRunRepository.byId.put(runId.value(), run);
    systemContextPort.criticality = tier;
    sourceControlPort.diff = List.of(
        new FileDiff("src/App.java", 1, 1, FileDiff.ChangeType.MODIFIED));
    // Synthesize a deterministic assessment so EvaluatePolicy can run.
    RiskAssessment risk = new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        com.cdi.risk.domain.RiskScore.of(level == RiskLevel.HIGH ? 70 : 20),
        level, List.of(), com.cdi.risk.domain.EvidenceState.NO_RELEVANT_EVIDENCE,
        DeterministicRiskEngine.RULE_VERSION, NOW);
    riskAssessmentRepository.byRun.put(runId.value(), risk);
  }

  // ---- fakes (shared across all stages) ----

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

  private class FakeAnalysisRunRepository implements AnalysisRunRepository {
    final Map<UUID, AnalysisRun> byId = new HashMap<>();
    final Map<UUID, TenantId> runTenants = new HashMap<>();

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
      return run != null && run.getStatus() == AnalysisRun.Status.QUEUED;
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

  private static class FakeRiskAssessmentRepository implements RiskAssessmentRepository {
    final Map<UUID, RiskAssessment> byRun = new HashMap<>();
    final List<RiskAssessment> saved = new ArrayList<>();

    @Override
    public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
      saved.add(riskAssessment);
      byRun.put(riskAssessment.getAnalysisRunId().value(), riskAssessment);
      return riskAssessment;
    }

    @Override
    public Optional<RiskAssessment> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byRun.get(analysisRunId.value()));
    }
  }

  private static class FakePolicyRepository implements PolicyRepository {
    Policy policy;

    @Override
    public Optional<Policy> findActiveByTenant(TenantId tenantId) {
      return Optional.ofNullable(policy);
    }

    @Override
    public Optional<Policy> findByTenantIdAndId(TenantId tenantId, PolicyId policyId) {
      if (policy != null && policy.getId().equals(policyId)) {
        return Optional.of(policy);
      }
      return Optional.empty();
    }

    @Override
    public Policy save(Policy policy) {
      this.policy = policy;
      return policy;
    }
  }

  private static class FakeDecisionRecordRepository implements DecisionRecordRepository {
    final Map<UUID, DecisionRecord> byRun = new HashMap<>();
    final List<DecisionRecord> saved = new ArrayList<>();

    @Override
    public DecisionRecord save(TenantId tenantId, DecisionRecord decisionRecord) {
      saved.add(decisionRecord);
      byRun.put(decisionRecord.getAnalysisRunId().value(), decisionRecord);
      return decisionRecord;
    }

    @Override
    public Optional<DecisionRecord> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byRun.get(analysisRunId.value()));
    }
  }

  private static class FakeAgentInvestigationRepository implements AgentInvestigationRepository {
    final Map<UUID, AgentInvestigation> byRunId = new HashMap<>();
    final List<AgentInvestigation> saved = new ArrayList<>();

    @Override
    public AgentInvestigation save(TenantId tenantId, AgentInvestigation investigation) {
      saved.add(investigation);
      byRunId.put(investigation.getAnalysisRunId().value(), investigation);
      return investigation;
    }

    @Override
    public Optional<AgentInvestigation> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byRunId.get(analysisRunId.value()));
    }

    @Override
    public Optional<AgentInvestigation> findById(TenantId tenantId, InvestigationId investigationId) {
      return saved.stream().filter(i -> i.getId().equals(investigationId)).findFirst();
    }
  }

  private static class FakeSourceControlPort implements SourceControlPort {
    List<FileDiff> diff = List.of();
    final List<StatusCall> statusChecks = new ArrayList<>();

    @Override
    public ChangeMetadata getChangeMetadata(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return new ChangeMetadata(providerChangeId, "t", "", "a", "feature-x", "main", COMMIT_SHA);
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      return diff;
    }

    @Override
    public void publishStatusCheck(
        TenantId tenantId, RepositoryId repositoryId, String commitSha,
        DecisionOutcome outcome, List<DecisionReason> reasons, String detailsUrl) {
      statusChecks.add(new StatusCall(tenantId, repositoryId, commitSha, outcome, reasons, detailsUrl));
    }

    record StatusCall(
        TenantId tenantId, RepositoryId repositoryId, String commitSha,
        DecisionOutcome outcome, List<DecisionReason> reasons, String detailsUrl) {}
  }

  private static class FakeSystemContextPort implements SystemContextPort {
    CriticalityTier criticality = CriticalityTier.TIER_3;

    @Override
    public CriticalityTier getServiceCriticality(
        TenantId tenantId, RepositoryId repositoryId, List<String> filePaths) {
      return criticality;
    }

    @Override
    public List<ServiceDependency> getDependencies(
        TenantId tenantId, com.cdi.common.domain.id.ServiceId serviceId) {
      return List.of();
    }
  }

  private static class FakeEvidenceSearchPort implements EvidenceSearchPort {
    List<EvidenceRecord> records = List.of();

    @Override
    public List<EvidenceRecord> searchSimilarChanges(
        TenantId tenantId, List<String> filePaths, int limit) {
      return records;
    }

    @Override
    public List<EvidenceRecord> searchIncidents(
        TenantId tenantId, com.cdi.common.domain.id.ServiceId serviceId,
        List<String> keywords, int limit) {
      return List.of();
    }
  }

  private static class FakeAgentPort implements AgentPort {
    InvestigationFindings output;
    boolean fail;

    @Override
    public InvestigationFindings investigate(
        AgentContext context, RiskAssessment riskAssessment, List<EvidenceRecord> evidence) {
      if (fail) {
        throw new PortException(PortType.AGENT, true, "agent timeout");
      }
      return output;
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
      // not used by the chain
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
