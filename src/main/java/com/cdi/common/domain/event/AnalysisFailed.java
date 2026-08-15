package com.cdi.common.domain.event;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Supplementary run-lifecycle event for audit only (application-layer.md §12).
 * Emitted by UC-03 AnalyzeChange when the run terminates in {@code FAILED}
 * after a per-port terminal failure (application-layer.md §9.2): the run is
 * never silently lost — a visible outcome is always recorded.
 *
 * <p>Carrier: {@code analysisRunId, changeId, failureCategory, failureCode,
 * failedAt}, scoped by {@code tenantId}.
 */
public record AnalysisFailed(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    AnalysisRunId analysisRunId,
    ChangeId changeId,
    AnalysisFailure.FailureCategory failureCategory,
    String failureCode,
    Instant failedAt) implements DomainEvent {

  public AnalysisFailed {
    if (eventId == null) {
      throw new DomainException("Event ID cannot be null");
    }
    if (occurredOn == null) {
      throw new DomainException("Occurred-on timestamp cannot be null");
    }
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
    if (failureCategory == null) {
      throw new DomainException("Failure category cannot be null");
    }
    if (failedAt == null) {
      throw new DomainException("Failed-at timestamp cannot be null");
    }
    failureCode = failureCode != null ? failureCode.trim() : "";
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static AnalysisFailed create(
      TenantId tenantId,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      AnalysisFailure.FailureCategory failureCategory,
      String failureCode,
      Instant failedAt) {
    return new AnalysisFailed(
        UUID.randomUUID(), Instant.now(), tenantId,
        analysisRunId, changeId, failureCategory, failureCode, failedAt);
  }
}