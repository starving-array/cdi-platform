package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.risk.domain.RiskAssessment;

import java.util.List;

/**
 * Read result for UC-11 GetChange (application-layer.md §6): the change
 * aggregate, its analysis-run history (each run is one evaluated commit
 * snapshot), and the latest run's risk and decision.
 *
 * <p><b>History ordering</b>: {@code runs} is ordered oldest→newest by
 * {@code createdAt} (deterministic history of prior commit SHAs,
 * application-layer.md §6 UC-11, use-cases.md §6.1). Empty history is
 * represented by an empty list.
 *
 * <p><b>Latest artifacts</b>: a decision is unique per run
 * ({@code UNIQUE (tenant_id, analysis_run_id)}, data-model.md §E), so
 * {@code latestRisk}/{@code latestDecision} are the most recent run's
 * artifacts — {@code null} when the run history is empty or the latest run has
 * not produced them yet. Immutable; read-only, never mutated by the query
 * service.
 */
public record ChangeAnalysisView(
    Change change,
    List<AnalysisRun> runs,
    RiskAssessment latestRisk,
    DecisionRecord latestDecision) {

  public ChangeAnalysisView {
    if (change == null) {
      throw new DomainException("Change cannot be null");
    }
    if (runs == null) {
      throw new DomainException("Runs cannot be null");
    }
    runs = List.copyOf(runs);
  }
}
