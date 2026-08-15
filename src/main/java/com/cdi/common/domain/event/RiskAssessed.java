package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.risk.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical domain event emitted when the deterministic risk calculation for
 * an analysis run finishes (domain-events.md §4.2, application-layer.md §12;
 * producer UC-03 AnalyzeChange). Consumed by the agent orchestrator
 * (decides if {@code InvestigateRisk} should be enqueued) and the policy
 * evaluator.
 *
 * <p>Carrier shape follows application-layer.md §12: {@code analysisRunId,
 * changeId, riskAssessmentId, riskLevel}, scoped by {@code tenantId}. Published
 * by the application layer through {@code DomainEventPublisher} in the same
 * transaction as the persisted risk assessment and completed run
 * (application-layer.md §8 UC-03).
 */
public record RiskAssessed(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    AnalysisRunId analysisRunId,
    ChangeId changeId,
    RiskAssessmentId riskAssessmentId,
    RiskLevel riskLevel) implements DomainEvent {

  public RiskAssessed {
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
    if (riskAssessmentId == null) {
      throw new DomainException("RiskAssessmentId cannot be null");
    }
    if (riskLevel == null) {
      throw new DomainException("RiskLevel cannot be null");
    }
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static RiskAssessed create(
      TenantId tenantId,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      RiskAssessmentId riskAssessmentId,
      RiskLevel riskLevel) {
    return new RiskAssessed(
        UUID.randomUUID(), Instant.now(), tenantId,
        analysisRunId, changeId, riskAssessmentId, riskLevel);
  }
}