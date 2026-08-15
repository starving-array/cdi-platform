package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Idempotent command result: the analysis run produced (or reused) by an
 * idempotent intake/analysis command, plus whether it was newly created
 * (application-layer.md §6).
 */
public record IdempotentCommandResult(AnalysisRunId analysisRunId, boolean created)
    implements CommandResult {

  public IdempotentCommandResult {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}