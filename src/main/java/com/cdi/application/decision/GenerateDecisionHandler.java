package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use case UC-06 final decision delivery — the downstream
 * <em>status-publication</em> stage after UC-05 policy evaluation
 * (analysis-workflow.md §1.8, application-layer.md §5/§6/§12, use-cases.md
 * §5.4). Async, consumed from the queue via {@code JobQueuePort} with the
 * run-only payload {@code GenerateDecisionCommand}.
 *
 * <p><b>Scope / separation (§1.8, §6)</b>: policy evaluation — resolving the
 * tenant {@code Policy}, running the domain {@link PolicyEngine}, and
 * persisting the {@link DecisionRecord} — is the upstream UC-05 stage
 * ({@link EvaluatePolicyHandler}) and is deliberately <em>not</em> re-run
 * here. This stage loads the already-persisted final {@code DecisionRecord}
 * and delivers the decision's source-control status check downstream. It never
 * recomputes risk, never invokes {@code PolicyEngine}, and never creates a
 * second decision.
 *
 * <p><b>Delivery (§5, §12, use-cases.md §5.4)</b>: the documented source-control
 * status side effect for the final decision is fired through
 * {@link SourceControlPort#publishStatusCheck(...)} with the persisted
 * decision's outcome and reasons, bound to the run's snapshot commit. UC-05
 * owns the {@code DecisionGenerated} event; this stage does not re-emit it.
 * The handler performs no DB write — the decision is already persisted by
 * UC-05 — so the external status call is trivially outside any write
 * transaction (§8 rule 4).
 *
 * <p><b>Preconditions (§4, §6)</b>: only a {@code COMPLETED} run (which holds a
 * finalized decision) with a persisted final {@code DecisionRecord} is
 * eligible. Missing data is surfaced as a typed {@link ApplicationException},
 * never swallowed and never a generic {@code RuntimeException}.
 *
 * <p><b>Idempotency (§10/§11) / failure policy</b>: the final decision is a
 * single immutable {@code DecisionRecord} per run (UC-05, backstopped by
 * {@code UNIQUE (tenant_id, analysis_run_id)}), so a repeat invocation posts
 * the same deterministic status and never duplicates a decision. A
 * {@code PortException} from the status call surfaces so the delivery layer can
 * retry the side effect; it never erases the already-persisted decision.
 */
public final class GenerateDecisionHandler {

  private final AnalysisRunRepository analysisRunRepository;
  private final ChangeRepository changeRepository;
  private final DecisionRecordRepository decisionRecordRepository;
  private final SourceControlPort sourceControlPort;

  public GenerateDecisionHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      DecisionRecordRepository decisionRecordRepository,
      SourceControlPort sourceControlPort) {
    this.analysisRunRepository =
        Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.decisionRecordRepository =
        Objects.requireNonNull(decisionRecordRepository, "DecisionRecordRepository");
    this.sourceControlPort = Objects.requireNonNull(sourceControlPort, "SourceControlPort");
  }

  /**
   * Delivers the persisted final decision to source control as a status check.
   * Deterministic and replay-safe; performs no writes and no re-evaluation.
   */
  public void handle(GenerateDecisionCommand command) {
    Optional<AnalysisRunContext> context = analysisRunRepository.findById(command.analysisRunId());
    if (context.isEmpty()) {
      throw new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
          Map.of("reason", "analysis-not-found"));
    }
    AnalysisRunContext runContext = context.get();
    AnalysisRun run = runContext.run();
    TenantId tenantId = runContext.tenantId();

    if (run.getStatus() != AnalysisRun.Status.COMPLETED) {
      throw new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
          Map.of("reason", "analysis-not-completed", "status", run.getStatus().name()));
    }

    DecisionRecord decision = decisionRecordRepository
        .findByAnalysisRunId(tenantId, run.getId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.POLICY_EVALUATION_FAILED,
            Map.of("reason", "policy-evaluation-missing")));

    Change change = changeRepository.findByTenantAndId(tenantId, run.getChangeId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.CHANGE_NOT_FOUND,
            Map.of("reason", "change-not-found")));

    publishStatusCheck(tenantId, run, change, decision);
  }

  private void publishStatusCheck(
      TenantId tenantId, AnalysisRun run, Change change, DecisionRecord decision) {
    sourceControlPort.publishStatusCheck(
        tenantId,
        change.getRepositoryId(),
        run.getCodeSnapshot().commitSha(),
        decision.getOutcome(),
        List.copyOf(decision.getReasons()),
        detailsUrl(run));
  }

  private String detailsUrl(AnalysisRun run) {
    return "/runs/" + run.getId().value() + "/decision";
  }
}
