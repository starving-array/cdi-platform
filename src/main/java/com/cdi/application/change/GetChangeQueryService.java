package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetChangeBySourceQuery;
import com.cdi.application.port.in.GetChangeQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Application query service UC-11 GetChange (application-layer.md §6/§4,
 * use-cases.md §6.1). Synchronous, read-only: assembles the tenant-scoped
 * {@link ChangeAnalysisView} from existing aggregates and read-only port
 * calls. Never mutates state, never opens a write transaction, never calls an
 * external port, and is never idempotency-keyed (queries are not keyed —
 * application-layer.md §6).
 *
 * <p><b>Lookup forms</b>: primary by {@link GetChangeQuery ChangeId} (the
 * tenant is resolved from the request context) or by the SCM source reference
 * {@link GetChangeBySourceQuery (tenantId, repositoryId, providerChangeId)}
 * (application-foundation.md §6).
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): every repository read is
 * tenant-scoped; a change that does not belong to the effective tenant
 * resolves to {@link ApplicationError#CHANGE_NOT_FOUND} — no cross-tenant
 * read is possible.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-11's
 * actor is {@code ENGINEER}.
 */
public final class GetChangeQueryService {

  private final ChangeRepository changeRepository;
  private final AnalysisRunRepository analysisRunRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final DecisionRecordRepository decisionRecordRepository;

  public GetChangeQueryService(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      DecisionRecordRepository decisionRecordRepository) {
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "RiskAssessmentRepository");
    this.decisionRecordRepository = Objects.requireNonNull(decisionRecordRepository, "DecisionRecordRepository");
  }

  /**
   * Resolves the change by its primary id, scoped to the tenant resolved from
   * the request context.
   */
  public ChangeAnalysisView handle(GetChangeQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    Change change = changeRepository.findByTenantAndId(tenantId, query.changeId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.CHANGE_NOT_FOUND));
    return assemble(tenantId, change);
  }

  /**
   * Resolves the change by its SCM source reference (tenant-scoped).
   */
  public ChangeAnalysisView handle(GetChangeBySourceQuery query, Actor actor) {
    requireRole(actor);
    Change change = changeRepository.findByTenantAndRepositoryAndProvider(
        query.tenantId(), query.repositoryId(), query.providerChangeId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.CHANGE_NOT_FOUND));
    return assemble(query.tenantId(), change);
  }

  private ChangeAnalysisView assemble(TenantId tenantId, Change change) {
    List<AnalysisRunContext> history =
        analysisRunRepository.findByTenantAndChange(tenantId, change.getId());
    List<AnalysisRun> runs = history.stream().map(AnalysisRunContext::run).toList();
    if (runs.isEmpty()) {
      return new ChangeAnalysisView(change, List.of(), null, null);
    }
    AnalysisRun latest = runs.get(runs.size() - 1);
    return new ChangeAnalysisView(
        change,
        runs,
        riskAssessmentRepository.findByAnalysisRunId(tenantId, latest.getId()).orElse(null),
        decisionRecordRepository.findByAnalysisRunId(tenantId, latest.getId()).orElse(null));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}
