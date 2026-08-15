package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Worker payload for UC-04 InvestigateRisk (application-layer.md §6,
 * use-cases.md §5.3). Consumed from the queue via {@code JobQueuePort}; runs
 * the structured agent investigation over the run's deterministic risk
 * assessment and enqueues {@code EvaluatePolicy} afterwards so the workflow
 * continues to deterministic policy evaluation regardless of whether the
 * investigation succeeded or degraded to deterministic-only risk.
 */
public record InvestigateRiskCommand(AnalysisRunId analysisRunId) {

  public InvestigateRiskCommand {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}
