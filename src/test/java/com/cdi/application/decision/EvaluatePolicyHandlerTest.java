package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
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
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyEngine;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import com.cdi.systemcontext.domain.CriticalityTier;
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
 * Unit tests for UC-05 policy evaluation — the deterministic PolicyEngine
 * orchestration. Uses fake ports only; no Testcontainers, no Spring context,
 * no persistence, and the real domain {@link PolicyEngine}.
 */
class EvaluatePolicyHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String COMMIT_SHA = "abc123sha";

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();
  private final EvidenceId incidentId = EvidenceId.generate();

  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeChangeRepository changeRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakePolicyRepository policyRepository;
  private FakeDecisionRecordRepository decisionRecordRepository;
  private FakeSourceControlPort sourceControlPort;
  private FakeSystemContextPort systemContextPort;
  private FakeJobQueuePort jobQueuePort;
  private FakeDomainEventPublisher eventPublisher;
  private EvaluatePolicyHandler handler;

  @BeforeEach
  void setUp() {
    analysisRunRepository = new FakeAnalysisRunRepository(tenantId);
    changeRepository = new FakeChangeRepository();
    riskAssessmentRepository = new FakeRiskAssessmentRepository(tenantId);
    policyRepository = new FakePolicyRepository(tenantId);
    decisionRecordRepository = new FakeDecisionRecordRepository(tenantId);
    sourceControlPort = new FakeSourceControlPort();
    systemContextPort = new FakeSystemContextPort();
    jobQueuePort = new FakeJobQueuePort();
    eventPublisher = new FakeDomainEventPublisher();
    handler = new EvaluatePolicyHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        policyRepository, decisionRecordRepository, sourceControlPort,
        systemContextPort, new PolicyEngine(), jobQueuePort, eventPublisher, CLOCK);
  }

  @Test
  void validCompletedRunEvaluatesPolicyPersistsDecisionAndEnqueuesDelivery() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    policyRepository.policy = approveAllPolicy();

    DecisionRecord decision = handler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(DecisionOutcome.APPROVE, decision.getOutcome());
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(changeId, ((DecisionGenerated) eventPublisher.events.get(0)).changeId());
    assertEquals(runId, ((DecisionGenerated) eventPublisher.events.get(0)).analysisRunId());
    assertEquals(decision.getId(),
        ((DecisionGenerated) eventPublisher.events.get(0)).decisionId());

    // The final step toward delivery: enqueue the downstream UC-06 stage.
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("GenerateDecisionCommand", jobQueuePort.commandNames.get(0));
    assertEquals(runId, ((GenerateDecisionCommand) jobQueuePort.payloads.get(0)).analysisRunId());
    assertEquals(new IdempotencyKey(tenantId.value() + ":" + runId.value()),
        jobQueuePort.keys.get(0));
  }

  @Test
  void rejectsEvaluationForAnotherTenant() {
    TenantId otherTenant = TenantId.generate();
    AnalysisRun otherRun = completedRun(otherTenant, AnalysisRunId.generate());
    analysisRunRepository.byId.put(otherRun.getId().value(), otherRun);
    analysisRunRepository.runTenants.put(otherRun.getId().value(), otherTenant);
    changeRepository.byId.put(
        otherTenant.value() + "|" + otherRun.getChangeId().value(), openChangeFor(otherTenant));
    riskAssessmentRepository.riskByRun.put(otherRun.getId().value(), assessmentFor(otherRun));

    // The default-tenant fakes resolve nothing for the run's tenant, so
    // evaluation must fail closed and never persist a decision for it.
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new EvaluatePolicyCommand(otherRun.getId())));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void missingAnalysisRaisesTypedError() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new EvaluatePolicyCommand(AnalysisRunId.generate())));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("analysis-not-found", ex.getDetails().get("reason"));
  }

  @Test
  void nonCompletedAnalysisRaisesTypedError() {
    AnalysisRun run = queuedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    openChangeFor(tenantId);
    policyRepository.policy = approveAllPolicy();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new EvaluatePolicyCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("analysis-not-completed", ex.getDetails().get("reason"));
  }

  @Test
  void missingRiskAssessmentRaisesTypedError() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    policyRepository.policy = approveAllPolicy();
    riskAssessmentRepository.riskByRun.clear();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new EvaluatePolicyCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("risk-assessment-missing", ex.getDetails().get("reason"));
  }

  @Test
  void missingPolicyRaisesTypedError() {
    completedRunWithEverything(CriticalityTier.TIER_0);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new EvaluatePolicyCommand(runId)));
    assertEquals(ApplicationError.POLICY_EVALUATION_FAILED, ex.getError());
    assertEquals("policy-not-found", ex.getDetails().get("reason"));
    assertTrue(decisionRecordRepository.saved.isEmpty());
  }

  @Test
  void persistsExactPolicyVersionForTraceability() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    Policy policy = new Policy(
        PolicyId.generate(), tenantId, "P", "", PolicyStatus.ACTIVE,
        PolicyVersion.of("v2.1.0"),
        List.of(new PolicyRule("APPROVE_ALL", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(), "Approve all")),
        NOW);
    policyRepository.policy = policy;

    handler.handle(new EvaluatePolicyCommand(runId));

    DecisionRecord decision = decisionRecordRepository.saved.get(0);
    assertEquals("v2.1.0", decision.getPolicyVersion());
    assertEquals(policy.getId(), decision.getPolicyId());
  }

  @Test
  void precedenceResolvesToMostRestrictiveOutcome() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    Policy policy = new Policy(
        PolicyId.generate(), tenantId, "P", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        List.of(
            new PolicyRule("APPROVE_T0", Set.of(CriticalityTier.TIER_0), Set.of(),
                Set.of(), DecisionOutcome.APPROVE, List.of(), "Approve T0"),
            new PolicyRule("BLOCK_T0_HIGH", Set.of(CriticalityTier.TIER_0),
                Set.of(RiskLevel.HIGH), Set.of(), DecisionOutcome.BLOCK, List.of(),
                "Block T0 + HIGH")),
        NOW);
    policyRepository.policy = policy;

    DecisionRecord decision = handler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(DecisionOutcome.BLOCK, decision.getOutcome());
    assertEquals(2, decision.getReasons().size());
  }

  @Test
  void evaluationIsDeterministicAndRepeatable() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    policyRepository.policy = approveAllPolicy();

    DecisionRecord first = handler.handle(new EvaluatePolicyCommand(runId));
    // Re-invocation is an idempotent replay that returns the same decision.
    DecisionRecord second = handler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(first.getOutcome(), second.getOutcome());
    assertEquals(first.getPolicyVersion(), second.getPolicyVersion());
    assertEquals(1, decisionRecordRepository.saved.size());
    assertEquals(1, eventPublisher.events.size());
    // Re-invocation must not duplicate the downstream command (one creation,
    // one enqueue; the replay is a no-op that enqueues nothing).
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("GenerateDecisionCommand", jobQueuePort.commandNames.get(0));
  }

  @Test
  void policyPortFailureSurfacesNotSwallowed() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    policyRepository.fail = true;

    assertThrows(PortException.class,
        () -> handler.handle(new EvaluatePolicyCommand(runId)));
    assertTrue(decisionRecordRepository.saved.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void alreadyEvaluatedRunIsAnIdempotentNoOp() {
    completedRunWithEverything(CriticalityTier.TIER_0);
    policyRepository.policy = approveAllPolicy();
    DecisionRecord existing = DecisionRecord.builder()
        .tenantId(tenantId)
        .analysisRunId(runId)
        .riskAssessmentId(assessmentFor(completedRun(tenantId, runId)).getId())
        .policyId(PolicyId.generate())
        .policyVersion("v1")
        .outcome(DecisionOutcome.BLOCK)
        .reasons(List.of(new DecisionReason("x", "R", List.of())))
        .requiredActions(List.of())
        .generatedAt(NOW)
        .build();
    decisionRecordRepository.byRun.put(runId.value(), existing);

    DecisionRecord result = handler.handle(new EvaluatePolicyCommand(runId));

    assertEquals(existing, result);
    assertEquals(0, decisionRecordRepository.saved.size());
    assertTrue(eventPublisher.events.isEmpty());
    // Replay must not re-enqueue the downstream delivery stage (§10/§11).
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  // ---- helpers ----

  private void completedRunWithEverything(CriticalityTier tier) {
    AnalysisRun run = completedRun(tenantId, runId);
    analysisRunRepository.byId.put(runId.value(), run);
    changeRepository.byId.put(
        tenantId.value() + "|" + changeId.value(), openChangeFor(tenantId));
    riskAssessmentRepository.riskByRun.put(runId.value(), assessmentFor(run));
    sourceControlPort.diff = List.of(new FileDiff("src/App.java", 5, 2, FileDiff.ChangeType.MODIFIED));
    systemContextPort.tier = tier;
  }

  private RiskAssessment assessmentFor(AnalysisRun run) {
    return new RiskAssessment(
        RiskAssessmentId.generate(), run.getId(),
        RiskScore.of(65), RiskLevel.HIGH,
        List.of(new RiskFactor(RiskFactorType.HIGH_SERVICE_CRITICALITY, 30,
            "Tier-0 service affected.", List.of())),
        EvidenceState.EVIDENCE_AVAILABLE,
        DeterministicRiskEngine.RULE_VERSION, NOW);
  }

  private AnalysisRun completedRun(TenantId tenant, AnalysisRunId id) {
    AnalysisRun run = queuedRun(tenant, id);
    run.start();
    run.complete(NOW.plusSeconds(30));
    return run;
  }

  private AnalysisRun queuedRun(TenantId tenant, AnalysisRunId id) {
    return new AnalysisRun(
        id, changeId, new CodeSnapshot(COMMIT_SHA, "feature-x"), NOW);
  }

  private Change openChangeFor(TenantId tenant) {
    return new Change(
        changeId, repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", COMMIT_SHA, NOW);
  }

  private Policy approveAllPolicy() {
    return new Policy(
        PolicyId.generate(), tenantId, "Approve All", "", PolicyStatus.ACTIVE,
        PolicyVersion.of("v1"),
        List.of(new PolicyRule("APPROVE_ALL", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(RequiredAction.HUMAN_REVIEW), "Approve all")),
        NOW);
  }

  // ---- fakes ----

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

  private static class FakeRiskAssessmentRepository implements RiskAssessmentRepository {
    private final TenantId defaultTenant;
    final Map<UUID, RiskAssessment> riskByRun = new HashMap<>();

    FakeRiskAssessmentRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
      riskByRun.put(riskAssessment.getAnalysisRunId().value(), riskAssessment);
      return riskAssessment;
    }

    @Override
    public Optional<RiskAssessment> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
      return Optional.ofNullable(riskByRun.get(analysisRunId.value()));
    }
  }

  private static class FakePolicyRepository implements PolicyRepository {
    private final TenantId defaultTenant;
    Policy policy;
    boolean fail;

    FakePolicyRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public Optional<Policy> findActiveByTenant(TenantId tenantId) {
      if (fail) {
        throw new PortException(PortType.SYSTEM_CONTEXT, false, "policy repo down");
      }
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
      return Optional.ofNullable(policy);
    }

    @Override
    public Optional<Policy> findByTenantIdAndId(TenantId tenantId, PolicyId policyId) {
      if (fail) {
        throw new PortException(PortType.SYSTEM_CONTEXT, false, "policy repo down");
      }
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
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
    List<FileDiff> diff = List.of();

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
        com.cdi.decision.domain.DecisionOutcome outcome,
        List<com.cdi.decision.domain.DecisionReason> reasons, String detailsUrl) {
      // not used by UC-05 policy evaluation (final rendering is downstream)
    }
  }

  private static class FakeSystemContextPort implements SystemContextPort {
    CriticalityTier tier = CriticalityTier.TIER_0;

    @Override
    public CriticalityTier getServiceCriticality(
        TenantId tenantId, RepositoryId repositoryId, List<String> filePaths) {
      return tier;
    }

    @Override
    public List<com.cdi.systemcontext.domain.ServiceDependency> getDependencies(
        TenantId tenantId, com.cdi.common.domain.id.ServiceId serviceId) {
      return List.of();
    }
  }

  private static class FakeDomainEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
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
      // not used by EvaluatePolicy
    }
  }
}
