package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Supplementary run-lifecycle event for audit only (application-layer.md §12:
 * {@code AnalysisCompleted} / {@code AnalysisFailed} are coarser than the
 * rejected {@code ChangeAnalysisStarted}/{@code EvidenceCollected} and are not
 * part of the canonical reject list). Emitted by UC-03 AnalyzeChange when the
 * run exits {@code RUNNING} successfully.
 *
 * <p>Carrier: {@code analysisRunId, changeId, completedAt}, scoped by
 * {@code tenantId}. Published in the same transaction as the run completion
 * (application-layer.md §8 UC-03).
 */
public record AnalysisCompleted(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    AnalysisRunId analysisRunId,
    ChangeId changeId,
    Instant completedAt) implements DomainEvent {

  public AnalysisCompleted {
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
    if (completedAt == null) {
      throw new DomainException("Completed-at timestamp cannot be null");
    }
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static AnalysisCompleted create(
      TenantId tenantId,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      Instant completedAt) {
    return new AnalysisCompleted(
        UUID.randomUUID(), Instant.now(), tenantId, analysisRunId, changeId, completedAt);
  }
}