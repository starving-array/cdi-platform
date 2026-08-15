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
 * Canonical domain event emitted when the deterministic policy evaluation for
 * an analysis run produces and persists a {@code DecisionRecord}
 * (domain-events.md §4.4, application-layer.md §12; producer UC-05 policy
 * evaluation). Consumed by the status publisher (calls {@code SourceControlPort}
 * after commit) and the audit logger.
 *
 * <p>Carrier shape follows domain-events.md §4.4 / application-layer.md §12:
 * {@code analysisRunId, changeId, decisionId, outcome, policyVersion, commitSha},
 * scoped by {@code tenantId}. Published by the application layer through
 * {@code DomainEventPublisher} in the same transaction as the persisted
 * decision record (application-layer.md §8).
 */
public record DecisionGenerated(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    AnalysisRunId analysisRunId,
    ChangeId changeId,
    DecisionId decisionId,
    DecisionOutcome outcome,
    String policyVersion,
    String commitSha) implements DomainEvent {

  public DecisionGenerated {
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
    if (outcome == null) {
      throw new DomainException("Outcome cannot be null");
    }
    if (policyVersion == null || policyVersion.isBlank()) {
      throw new DomainException("Policy version cannot be blank");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static DecisionGenerated create(
      TenantId tenantId,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      DecisionId decisionId,
      DecisionOutcome outcome,
      String policyVersion,
      String commitSha) {
    return new DecisionGenerated(
        UUID.randomUUID(), Instant.now(), tenantId,
        analysisRunId, changeId, decisionId, outcome, policyVersion, commitSha);
  }
}
