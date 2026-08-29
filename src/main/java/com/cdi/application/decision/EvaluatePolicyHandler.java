package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.application.port.out.SystemContextPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DecisionGenerated;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyEngine;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application use case UC-05 policy evaluation — the deterministic
 * policy-evaluation stage after InvestigateRisk (analysis-workflow.md §1.7,
 * application-layer.md §6 UC-04/§5, use-cases.md §5.4). Async, consumed from
 * the queue via {@code JobQueuePort} with the run-only payload
 * {@code EvaluatePolicyCommand}.
 *
 * <p><b>Determinism / separation (§1.7, §8)</b>: this handler <em>orchestrates</em>
 * the existing domain {@link PolicyEngine}; it never recomputes risk, never
 * re-implements rule precedence or the fail-safe fallback, and never touches
 * {@code Policy}/{@code PolicyRule} semantics. Policy evaluation produces a
 * traceable {@link DecisionRecord}; rendering / posting the result to source
 * control is the separate downstream GenerateDecision stage and is out of scope
 * here (no {@code SourceControlPort.publishStatusCheck} call). This stage
 * enqueues {@code GenerateDecisionCommand} once the decision is persisted and
 * {@code DecisionGenerated} is published, handing the final decision to that
 * downstream delivery/status stage.
 *
 * <p><b>Traceability (§9)</b>: the persisted decision carries the exact
 * {@code PolicyId}/{@code PolicyVersion} that was evaluated and the
 * {@code RiskAssessmentId} — historical policy definitions are never mutated.
 *
 * <p><b>Preconditions (§4)</b>: only a {@code COMPLETED} run (which holds a
 * finalized deterministic assessment) is eligible. Missing data that prevents
 * policy evaluation is surfaced as a typed {@link ApplicationException}, never
 * swallowed and never a generic {@code RuntimeException}.
 *
 * <p><b>Idempotency (§10/§11)</b>: a run that already has a persisted decision
 * is a replay-safe no-op (application-layer.md §10); {@code UNIQUE
 * (tenant_id, analysis_run_id)} on {@code decision_record} is the DB backstop.
 * No distributed locks, no optimistic-locking seams beyond the existing
 * persistence constraints.
 */
public final class EvaluatePolicyHandler {

  private static final Logger log = LoggerFactory.getLogger(EvaluatePolicyHandler.class);
  private static final CriticalityTier DEGRADED_CRITICALITY = CriticalityTier.TIER_0;

  private final AnalysisRunRepository analysisRunRepository;
  private final ChangeRepository changeRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final PolicyRepository policyRepository;
  private final DecisionRecordRepository decisionRecordRepository;
  private final SourceControlPort sourceControlPort;
  private final SystemContextPort systemContextPort;
  private final PolicyEngine policyEngine;
  private final JobQueuePort jobQueuePort;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  public EvaluatePolicyHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      PolicyRepository policyRepository,
      DecisionRecordRepository decisionRecordRepository,
      SourceControlPort sourceControlPort,
      SystemContextPort systemContextPort,
      PolicyEngine policyEngine,
      JobQueuePort jobQueuePort,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "RiskAssessmentRepository");
    this.policyRepository = Objects.requireNonNull(policyRepository, "PolicyRepository");
    this.decisionRecordRepository = Objects.requireNonNull(decisionRecordRepository, "DecisionRecordRepository");
    this.sourceControlPort = Objects.requireNonNull(sourceControlPort, "SourceControlPort");
    this.systemContextPort = Objects.requireNonNull(systemContextPort, "SystemContextPort");
    this.policyEngine = Objects.requireNonNull(policyEngine, "PolicyEngine");
    this.jobQueuePort = Objects.requireNonNull(jobQueuePort, "JobQueuePort");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "DomainEventPublisher");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Evaluates the tenant policy against the run's deterministic risk and
   * persists the resulting decision. Idempotent and replay-safe.
   */
  public DecisionRecord handle(EvaluatePolicyCommand command) {

    Optional<AnalysisRunContext> context = analysisRunRepository.findById(command.analysisRunId());
    if (context.isEmpty()) {
      throw new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
          Map.of("reason", "analysis-not-found"));
    }
    AnalysisRunContext runContext = context.get();
    AnalysisRun run = runContext.run();
    TenantId tenantId = runContext.tenantId();

    Optional<DecisionRecord> existingDecision = decisionRecordRepository.findByAnalysisRunId(tenantId, run.getId());
    if (existingDecision.isPresent()) {
      enqueueGenerateDecision(tenantId, run.getId());
      return existingDecision.get();
    }

    if (run.getStatus() != AnalysisRun.Status.RUNNING && run.getStatus() != AnalysisRun.Status.COMPLETED) {
      throw new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
          Map.of("reason", "analysis-not-running", "status", run.getStatus().name()));
    }

    Change change = changeRepository.findByTenantAndId(tenantId, run.getChangeId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.CHANGE_NOT_FOUND,
            Map.of("reason", "change-not-found")));

    RiskAssessment risk = riskAssessmentRepository.findByAnalysisRunId(tenantId, run.getId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
            Map.of("reason", "risk-assessment-missing")));

    Policy policy = policyRepository.findActiveByTenant(tenantId)
        .orElseThrow(() -> new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
            Map.of("reason", "policy-not-found")));

    CriticalityTier tier = resolveCriticality(tenantId, change, run);
    
    DecisionRecord decision = evaluate(policy, risk, tier);

    decisionRecordRepository.save(tenantId, decision);

    publish(DecisionGenerated.create(
        tenantId, run.getId(), change.getId(), decision.getId(),
        decision.getOutcome(), decision.getPolicyVersion(),
        run.getCodeSnapshot().commitSha()));
    
    enqueueGenerateDecision(tenantId, run.getId());
    
    return decision;
  }

  /**
   * Hands the persisted decision to the downstream UC-06 delivery/status stage
   * (application-layer.md §1.8). Enqueued only on the create path; the
   * idempotent replay branch returns early before reaching here, so a repeat
   * EvaluatePolicy invocation never duplicates the downstream command
   * (§10/§11).
   */
  private void enqueueGenerateDecision(TenantId tenantId, AnalysisRunId runId) {
    jobQueuePort.enqueue("GenerateDecisionCommand", new GenerateDecisionCommand(runId),
        new IdempotencyKey(tenantId.value() + ":" + runId.value()));
  }

  /**
   * Delegates to the domain {@link PolicyEngine} — deterministic and
   * reproducible. A domain violation is translated to the policy-stage
   * application error, never a generic runtime exception.
   */
  private DecisionRecord evaluate(Policy policy, RiskAssessment risk, CriticalityTier tier) {
    try {
      return policyEngine.evaluate(policy, risk, tier, clock.instant());
    } catch (DomainException e) {
      throw new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
          Map.of("reason", "policy-evaluation-failed", "detail", e.getMessage()));
    }
  }

  /**
   * Resolves the {@code CriticalityTier} for the change (application-layer.md
   * §5). Catalog/diff unavailability degrades conservatively to the highest
   * risk tier ({@code TIER_0}), matching the fail-safe principle
   * (application-layer.md §9.2) — never a blocker.
   */
  private CriticalityTier resolveCriticality(TenantId tenantId, Change change, AnalysisRun run) {
    List<String> paths;
    try {
      List<FileDiff> diff =
          sourceControlPort.getDiff(tenantId, change.getRepositoryId(),
              run.getCodeSnapshot().commitSha());
      paths = diff.stream().map(FileDiff::path).toList();
    } catch (PortException e) {
      return DEGRADED_CRITICALITY;
    }
    try {
      return systemContextPort.getServiceCriticality(
          tenantId, change.getRepositoryId(), paths);
    } catch (PortException e) {
      return DEGRADED_CRITICALITY;
    }
  }

  private void publish(DomainEvent event) {
    eventPublisher.publish(event);
  }
}
