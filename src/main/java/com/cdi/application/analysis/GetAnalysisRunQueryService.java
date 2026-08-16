package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetAnalysisRunQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.risk.domain.RiskAssessment;

import java.util.Objects;

/**
 * Application query service UC-12 GetAnalysisRun (application-layer.md §6/§4,
 * api-contract.md §3.4). Synchronous, read-only: resolves a single analysis
 * run (the surface for the async 202 tracking URL) and assembles the
 * tenant-scoped {@link AnalysisRunView} from existing read-only port calls.
 * Never mutates state, never opens a write transaction, never calls an
 * external port, and is never idempotency-keyed (queries are not keyed —
 * application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link GetAnalysisRunQuery analysisRunId}; the
 * tenant is resolved from the request context (data-model.md §2 — the run
 * id alone is the worker/API payload).
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): the run's tenant is
 * resolved together with the aggregate via
 * {@link AnalysisRunRepository#findById}; a run that does not belong to the
 * context tenant resolves to
 * {@link ApplicationError#ANALYSIS_RUN_NOT_FOUND} — no cross-tenant read is
 * possible.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-12's
 * actor is {@code ENGINEER}.
 */
public final class GetAnalysisRunQueryService {

  private final AnalysisRunRepository analysisRunRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final DecisionRecordRepository decisionRecordRepository;

  public GetAnalysisRunQueryService(
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      DecisionRecordRepository decisionRecordRepository) {
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "RiskAssessmentRepository");
    this.decisionRecordRepository = Objects.requireNonNull(decisionRecordRepository, "DecisionRecordRepository");
  }

  public AnalysisRunView handle(GetAnalysisRunQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    AnalysisRunContext context = analysisRunRepository.findById(query.analysisRunId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.ANALYSIS_RUN_NOT_FOUND));
    if (!context.tenantId().equals(tenantId)) {
      throw new ApplicationException(ApplicationError.ANALYSIS_RUN_NOT_FOUND);
    }
    AnalysisRun run = context.run();
    RiskAssessmentId latestRiskAssessmentId = riskAssessmentRepository
        .findByAnalysisRunId(tenantId, run.getId())
        .map(RiskAssessment::getId)
        .orElse(null);
    DecisionId latestDecisionId = decisionRecordRepository
        .findByAnalysisRunId(tenantId, run.getId())
        .map(DecisionRecord::getId)
        .orElse(null);
    return new AnalysisRunView(run, latestRiskAssessmentId, latestDecisionId,
        run.getFailureInfo().orElse(null));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}
