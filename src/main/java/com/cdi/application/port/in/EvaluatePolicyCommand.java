package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Worker payload for UC-05 policy evaluation (application-layer.md §6,
 * analysis-workflow.md §1.7). Consumed from the queue via {@code JobQueuePort};
 * resolves the tenant {@code Policy} against the run's completed deterministic
 * {@code RiskAssessment} using the domain {@code PolicyEngine} and persists the
 * resulting {@code DecisionRecord}.
 *
 * <p>The payload carries only the run id (data-model.md §2 keeps
 * {@code TenantId} off the aggregate); the persistence adapter resolves the
 * tenant from the {@code analysis_run} row.
 */
public record EvaluatePolicyCommand(AnalysisRunId analysisRunId) {

  public EvaluatePolicyCommand {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}
