package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical domain event emitted when UC-06 OverrideDecision persists the
 * single immutable human override of a decision (domain-events.md §4.6,
 * application-layer.md §12; producer UC-06 OverrideDecision). Consumed by the
 * audit logger.
 *
 * <p>Carrier shape mirrors {@link DecisionGenerated} (domain-events.md §4.4 /
 * application-layer.md §12): {@code analysisRunId, changeId, decisionId,
 * originalOutcome, outcome, commitSha}, scoped by {@code tenantId}. The
 * {@code outcome} is the post-override effective outcome; {@code originalOutcome}
 * keeps the event self-contained for audit (ADR-006). Published by the
 * application layer through {@code DomainEventPublisher} in the same
 * transaction as the persisted override (application-layer.md §8).
 */
public record DecisionOverridden(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    AnalysisRunId analysisRunId,
    ChangeId changeId,
    DecisionId decisionId,
    DecisionOutcome originalOutcome,
    DecisionOutcome outcome,
    String commitSha) implements DomainEvent {

  public DecisionOverridden {
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
    if (decisionId == null) {
      throw new DomainException("DecisionId cannot be null");
    }
    if (originalOutcome == null) {
      throw new DomainException("OriginalOutcome cannot be null");
    }
    if (outcome == null) {
      throw new DomainException("Outcome cannot be null");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static DecisionOverridden create(
      TenantId tenantId,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      DecisionId decisionId,
      DecisionOutcome originalOutcome,
      DecisionOutcome outcome,
      String commitSha) {
    return new DecisionOverridden(
        UUID.randomUUID(), Instant.now(), tenantId,
        analysisRunId, changeId, decisionId, originalOutcome, outcome, commitSha);
  }
}