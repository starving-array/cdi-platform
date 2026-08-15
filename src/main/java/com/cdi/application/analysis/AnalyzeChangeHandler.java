package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.error.PortException;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.application.port.out.SystemContextPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.AnalysisCompleted;
import com.cdi.common.domain.event.AnalysisFailed;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.event.RiskAssessed;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.SourceType;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskAssessmentInput;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use case UC-03 AnalyzeChange — the background worker
 * orchestrator (application-layer.md §6, §5.1/§11; use-cases.md §5.2). Async,
 * consumed from the queue via {@code JobQueuePort} with the run-only payload
 * {@code AnalyzeChangeCommand}; actor is SYSTEM_WORKER.
 *
 * <p>No global transaction (§8): only the final "persist risk + complete run +
 * enqueue + outbox events" step is one short tx (conceptually; the queue is the
 * same-DB row). External calls (diff, criticality, evidence) are made outside
 * any transaction and are thin one-liners per §5.1 — retries/degradation come
 * from the port failure contract (§9) and are <em>not</em> implemented inline:
 * a surviving {@code PortException} is translated to the documented terminal
 * outcome (fail the run via {@link AnalysisRun#fail} or degrade).
 *
 * <p>Replay safety (§10/§11.2): the run is claimed with an atomic
 * {@code QUEUED→RUNNING} CAS; {@code claim()==false} (already owned, completed,
 * failed, or superseded) is an idempotent no-op. {@code findById} resolves the
 * run together with its tenant (the payload carries only the run id,
 * data-model.md §2). A missing run/change is a terminal data-integrity failure
 * (run FAILED, visible outcome — fail-safe principle, §9.2).
 *
 * <p>Step assembly: diff → criticality → evidence → deterministic risk. The
 * dependency count is {@code 0}: {@code SystemContextPort.getDependencies}
 * requires a {@code ServiceId} that the change/run aggregates do not carry in
 * P0 (only {@code repositoryId} on {@link Change}), so
 * {@code downstreamDependencyCount} stays unresolvable and the
 * {@code HIGH_DEPENDENCY_IMPACT} factor is dormant until a service-lookup seam
 * exists. Matched incidents are the {@code INCIDENT}/{@code POSTMORTEM} records
 * within the {@code searchSimilarChanges} results (searchIncidents also needs a
 * ServiceId); otherwise the state is {@code NO_RELEVANT_EVIDENCE}, and a
 * retrieval failure degrades to zero evidence with
 * {@code EVIDENCE_RETRIEVAL_FAILED} (risk-engine.md §8).
 */
public final class AnalyzeChangeHandler {

  private static final int EVIDENCE_LIMIT = 20;
  private static final CriticalityTier DEGRADED_CRITICALITY = CriticalityTier.TIER_0;

  private final ChangeRepository changeRepository;
  private final AnalysisRunRepository analysisRunRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final SourceControlPort sourceControlPort;
  private final SystemContextPort systemContextPort;
  private final EvidenceSearchPort evidenceSearchPort;
  private final JobQueuePort jobQueuePort;
  private final DomainEventPublisher eventPublisher;
  private final DeterministicRiskEngine riskEngine;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public AnalyzeChangeHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      SourceControlPort sourceControlPort,
      SystemContextPort systemContextPort,
      EvidenceSearchPort evidenceSearchPort,
      JobQueuePort jobQueuePort,
      DomainEventPublisher eventPublisher,
      DeterministicRiskEngine riskEngine,
      Clock clock) {
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "RiskAssessmentRepository");
    this.sourceControlPort = Objects.requireNonNull(sourceControlPort, "SourceControlPort");
    this.systemContextPort = Objects.requireNonNull(systemContextPort, "SystemContextPort");
    this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "EvidenceSearchPort");
    this.jobQueuePort = Objects.requireNonNull(jobQueuePort, "JobQueuePort");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "DomainEventPublisher");
    this.riskEngine = Objects.requireNonNull(riskEngine, "DeterministicRiskEngine");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Creates the handler using the system clock.
   */
  public AnalyzeChangeHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      SourceControlPort sourceControlPort,
      SystemContextPort systemContextPort,
      EvidenceSearchPort evidenceSearchPort,
      JobQueuePort jobQueuePort,
      DomainEventPublisher eventPublisher,
      DeterministicRiskEngine riskEngine) {
    this(changeRepository, analysisRunRepository, riskAssessmentRepository,
        sourceControlPort, systemContextPort, evidenceSearchPort,
        jobQueuePort, eventPublisher, riskEngine, Clock.systemUTC());
  }

  /**
   * Executes the analyze-change orchestration for a claimed run. Replay-safe:
   * a lost CAS claim or a missing run is a silent no-op.
   */
  public void handle(AnalyzeChangeCommand command) {
    Optional<AnalysisRunContext> context = analysisRunRepository.findById(command.analysisRunId());
    if (context.isEmpty()) {
      return;
    }
    AnalysisRunContext runContext = context.get();
    AnalysisRun run = runContext.run();
    if (!analysisRunRepository.claim(run.getId())) {
      return; // someone else owns the run, or it is no longer QUEUED → idempotent replay no-op
    }
    try {
      run.start(); // keep the in-memory aggregate aligned with the claimed DB row
    } catch (RuntimeException e) {
      return; // defensive: claim returned true but aggregate was not QUEUED — treat as no-op
    }

    TenantId tenantId = runContext.tenantId();
    Instant now = clock.instant();

    Change change = changeRepository.findByTenantAndId(tenantId, run.getChangeId()).orElse(null);
    if (change == null) {
      failRun(runContext, AnalysisFailure.FailureCategory.ANALYSIS_FAILED, "change-not-found", now);
      return;
    }

    List<FileDiff> diff;
    try {
      diff = sourceControlPort.getDiff(
          tenantId, change.getRepositoryId(), run.getCodeSnapshot().commitSha());
    } catch (PortException e) {
      failRun(runContext, AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, "scm-unavailable", now);
      return;
    }

    CriticalityTier criticality = resolveCriticality(tenantId, change, diff);
    Evidence evidence = resolveEvidence(tenantId, diff);
    List<String> paths = diff.stream().map(FileDiff::path).toList();

    RiskAssessmentInput input = new RiskAssessmentInput(
        diff.size(),
        diff.stream().mapToInt(FileDiff::additions).sum(),
        diff.stream().mapToInt(FileDiff::deletions).sum(),
        isDatabaseMigration(diff),
        isConfigurationChange(diff),
        criticality,
        0,
        evidence.state(),
        evidence.matchedIncidentIds());

    RiskAssessment assessment = riskEngine.assess(run.getId(), input, now);
    riskAssessmentRepository.save(tenantId, assessment);

    run.complete(now);
    analysisRunRepository.save(tenantId, run);

    enqueueNextStage(tenantId, run, assessment, criticality);

    publish(RiskAssessed.create(tenantId, run.getId(), change.getId(),
        assessment.getId(), assessment.getLevel()));
    publish(AnalysisCompleted.create(tenantId, run.getId(), change.getId(), now));
  }

  /**
   * Routes the completed run to the next workflow stage per
   * analysis-workflow.md §5 / application-layer.md §13: high-risk or Tier-0
   * changes investigate via the agent (InvestigateRisk) before policy
   * evaluation; everything else goes straight to deterministic policy
   * evaluation (EvaluatePolicy). The "evidence conflict" and "explicit
   * manual request" triggers named in §5 are not modeled in the current
   * evidence state enum nor in the worker-only payload (the
   * {@code AnalyzeChangeCommand} carries only the run id), so they remain a
   * future seam — mirroring the dormant {@code HIGH_DEPENDENCY_IMPACT} factor.
   * No new business rule: the two implemented triggers are taken verbatim
   * from the authoritative workflow.
   */
  private void enqueueNextStage(
      TenantId tenantId, AnalysisRun run, RiskAssessment assessment, CriticalityTier criticality) {
    IdempotencyKey key = new IdempotencyKey(tenantId.value() + ":" + run.getId().value());
    if (requiresInvestigation(assessment, criticality)) {
      jobQueuePort.enqueue("InvestigateRiskCommand", new InvestigateRiskCommand(run.getId()), key);
    } else {
      jobQueuePort.enqueue("EvaluatePolicyCommand", new EvaluatePolicyCommand(run.getId()), key);
    }
  }

  private boolean requiresInvestigation(RiskAssessment assessment, CriticalityTier criticality) {
    return assessment.getLevel() == RiskLevel.HIGH
        || assessment.getLevel() == RiskLevel.CRITICAL
        || criticality == CriticalityTier.TIER_0;
  }

  private CriticalityTier resolveCriticality(TenantId tenantId, Change change, List<FileDiff> diff) {
    List<String> paths = diff.stream().map(FileDiff::path).toList();
    try {
      return systemContextPort.getServiceCriticality(
          tenantId, change.getRepositoryId(), paths);
    } catch (PortException e) {
      // §9: catalog unavailable → degrade to conservative high-risk tier; proceed
      return DEGRADED_CRITICALITY;
    }
  }

  private Evidence resolveEvidence(TenantId tenantId, List<FileDiff> diff) {
    List<String> paths = diff.stream().map(FileDiff::path).toList();
    List<EvidenceRecord> records;
    try {
      records = evidenceSearchPort.searchSimilarChanges(tenantId, paths, EVIDENCE_LIMIT);
    } catch (PortException e) {
      // §9: no retry → degrade to zero evidence, state reflects the failure
      return new Evidence(EvidenceState.EVIDENCE_RETRIEVAL_FAILED, List.of());
    }
    if (records.isEmpty()) {
      return new Evidence(EvidenceState.NO_RELEVANT_EVIDENCE, List.of());
    }
    List<EvidenceId> incidents = records.stream()
        .filter(r -> r.getSource().sourceType() == SourceType.INCIDENT
            || r.getSource().sourceType() == SourceType.POSTMORTEM)
        .map(EvidenceRecord::getId)
        .toList();
    return new Evidence(EvidenceState.EVIDENCE_AVAILABLE, incidents);
  }

  private boolean isDatabaseMigration(List<FileDiff> diff) {
    return diff.stream().anyMatch(d -> {
      String p = d.path().toLowerCase();
      return p.contains("migration") || p.contains("db/schema") || p.endsWith(".sql");
    });
  }

  private boolean isConfigurationChange(List<FileDiff> diff) {
    return diff.stream().anyMatch(d -> {
      String p = d.path().toLowerCase();
      return p.endsWith(".yml") || p.endsWith(".yaml")
          || p.endsWith(".properties") || p.contains("/config/") || p.contains("config/");
    });
  }

  private void failRun(AnalysisRunContext context, AnalysisFailure.FailureCategory category,
      String failureCode, Instant now) {
    context.run().fail(new AnalysisFailure(category, failureCode, now));
    analysisRunRepository.save(context.tenantId(), context.run());
    publish(AnalysisFailed.create(
        context.tenantId(), context.run().getId(), context.run().getChangeId(),
        category, failureCode, now));
  }

  private void publish(DomainEvent event) {
    eventPublisher.publish(event);
  }

  private record Evidence(EvidenceState state, List<EvidenceId> matchedIncidentIds) {
  }
}