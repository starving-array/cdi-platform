package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Input contract for UC-12 GetAnalysisRun (application-layer.md §6): read the
 * state of a single analysis run (surface for the async tracking URL).
 */
public record GetAnalysisRunQuery(AnalysisRunId analysisRunId) {

  public GetAnalysisRunQuery {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}