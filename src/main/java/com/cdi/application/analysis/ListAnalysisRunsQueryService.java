package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListAnalysisRunsQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Application query service UC-17 ListAnalysisRuns (application-layer.md §6,
 * api-contract.md §3.2). Synchronous, read-only: resolves the change
 * tenant-scoped, then lists every {@link AnalysisRun} of that change (each
 * commit snapshot version) via the existing UC-11 tenant+change run history
 * query. Never mutates state, never opens a write transaction, never calls an
 * external port, and is never idempotency-keyed (queries are not keyed —
 * application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link ListAnalysisRunsQuery changeId}; the
 * tenant is resolved from the request context. The Change is resolved first
 * through the tenant-scoped {@link ChangeRepository#findByTenantAndId} so a
 * missing or cross-tenant change resolves to
 * {@link ApplicationError#CHANGE_NOT_FOUND} — never an empty list — then runs
 * are listed via the tenant+change query, preserving deterministic
 * oldest→newest ({@code createdAt} asc, {@code id} tie-breaker) ordering.
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): both reads are
 * tenant-scoped; a change that does not belong to the effective tenant
 * resolves to {@code CHANGE_NOT_FOUND}; a valid change with no runs yet
 * returns an empty list.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-17's
 * actor is {@code ENGINEER}.
 */
public final class ListAnalysisRunsQueryService {

  private final ChangeRepository changeRepository;
  private final AnalysisRunRepository analysisRunRepository;

  public ListAnalysisRunsQueryService(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository) {
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
  }

  public List<AnalysisRun> handle(ListAnalysisRunsQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    Change change = changeRepository.findByTenantAndId(tenantId, query.changeId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.CHANGE_NOT_FOUND));
    return analysisRunRepository.findByTenantAndChange(tenantId, change.getId())
        .stream()
        .map(AnalysisRunContext::run)
        .toList();
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}