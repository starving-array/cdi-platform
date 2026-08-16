package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;

/**
 * Input contract for UC-17 ListAnalysisRuns (application-layer.md §6,
 * api-contract.md §3.2). Lists every analysis run of a change (each commit
 * snapshot version) for the tenant resolved from the request context.
 */
public record ListAnalysisRunsQuery(ChangeId changeId) {

  public ListAnalysisRunsQuery {
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
  }
}