package com.cdi.application.port.out;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

/**
 * Tenant-scoped view of an {@code AnalysisRun} returned by
 * {@link AnalysisRunRepository#findById}.
 *
 * <p>{@code TenantId} does not live on the domain aggregate (data-model.md §2:
 * tenant is a port-call concern), but the UC-03 worker payload
 * {@code AnalyzeChangeCommand} carries only the run id. The persistence
 * adapter resolves the tenant from the {@code analysis_run} row it loads so
 * the worker can scope every downstream tenant-scoped port call
 * (SourceControl, SystemContext, EvidenceSearch, …) without leaking a
 * persistence concept into the aggregate.
 */
public record AnalysisRunContext(TenantId tenantId, AnalysisRun run) {

  public AnalysisRunContext {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (run == null) {
      throw new DomainException("AnalysisRun cannot be null");
    }
  }
}