package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Worker payload for UC-06 GenerateDecision (application-layer.md §6,
 * analysis-workflow.md §1.8). Consumed from the queue via {@code JobQueuePort}
 * after UC-05 policy evaluation has persisted the {@code DecisionRecord}; this
 * stage loads that already-persisted final decision and performs the
 * downstream source-control status/delivery side effect only. It does NOT
 * evaluate the tenant policy nor persist a {@code DecisionRecord} (that is
 * UC-05's responsibility).
 */
public record GenerateDecisionCommand(AnalysisRunId analysisRunId) {

  public GenerateDecisionCommand {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}