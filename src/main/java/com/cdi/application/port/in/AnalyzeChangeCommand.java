package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;

/**
 * Worker payload for UC-03 AnalyzeChange (application-layer.md §6). Consumed
 * from the queue via {@code JobQueuePort}; the handler is replay-safe (CAS
 * claim {@code QUEUED→RUNNING}, otherwise a no-op).
 */
public record AnalyzeChangeCommand(AnalysisRunId analysisRunId) {

  public AnalyzeChangeCommand {
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
  }
}