package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.RiskAssessmentId;

/**
 * Read result for UC-12 GetAnalysisRun (application-layer.md §6): the run
 * itself plus the ids of its latest risk assessment and decision (null when
 * the run has not produced them yet) and the run's failure (null unless the
 * run is {@code FAILED}).
 *
 * <p><b>Latest artifacts</b>: a risk assessment and a decision are each unique
 * per run ({@code UNIQUE (tenant_id, analysis_run_id)}, data-model.md §D/§E),
 * so the view's ids are the <em>requested</em> run's own artifacts — never
 * resolved from another run. Immutable; read-only, never mutated by the query
 * service.
 */
public record AnalysisRunView(
    AnalysisRun run,
    RiskAssessmentId latestRiskAssessmentId,
    DecisionId latestDecisionId,
    AnalysisFailure failure) {

  public AnalysisRunView {
    if (run == null) {
      throw new DomainException("AnalysisRun cannot be null");
    }
  }
}
