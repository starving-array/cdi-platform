package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical domain event emitted when an {@code AgentInvestigation} finishes
 * (domain-events.md §4.3, application-layer.md §12; producer UC-04
 * InvestigateRisk). Consumed by the policy evaluator (triggers
 * {@code GenerateDecision}).
 *
 * <p>Carrier follows domain-events.md §4.3: {@code investigationId, changeId,
 * status (SUCCESS/FAILED)}, scoped by {@code tenantId}.
 */
public record InvestigationCompleted(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    InvestigationId investigationId,
    ChangeId changeId,
    InvestigationOutcome outcome) implements DomainEvent {

  public enum InvestigationOutcome {
    SUCCESS, FAILED
  }

  public InvestigationCompleted {
    if (eventId == null) {
      throw new DomainException("Event ID cannot be null");
    }
    if (occurredOn == null) {
      throw new DomainException("Occurred-on timestamp cannot be null");
    }
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (investigationId == null) {
      throw new DomainException("InvestigationId cannot be null");
    }
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
    if (outcome == null) {
      throw new DomainException("Outcome cannot be null");
    }
  }

  public static InvestigationCompleted created(
      TenantId tenantId, InvestigationId investigationId, ChangeId changeId,
      InvestigationOutcome outcome) {
    return new InvestigationCompleted(
        UUID.randomUUID(), Instant.now(), tenantId, investigationId, changeId, outcome);
  }
}
