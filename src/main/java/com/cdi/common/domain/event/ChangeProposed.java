package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical domain event emitted when a change snapshot enters the system and
 * is queued for analysis (domain-events.md §4.1, application-layer.md §12).
 *
 * <p>Carrier shape follows application-layer.md §12: {@code tenantId,
 * repositoryId, providerChangeId, commitSha, analysisRunId}, plus the
 * {@code changeId} required by the canonical payload. Published by the
 * application layer through {@code DomainEventPublisher} only when new state
 * is actually created; idempotent reuse of an existing run publishes nothing.
 */
public record ChangeProposed(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    RepositoryId repositoryId,
    String providerChangeId,
    ChangeId changeId,
    String commitSha,
    AnalysisRunId analysisRunId) implements DomainEvent {

  public ChangeProposed {
    if (eventId == null) {
      throw new DomainException("Event ID cannot be null");
    }
    if (occurredOn == null) {
      throw new DomainException("Occurred-on timestamp cannot be null");
    }
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
    if (providerChangeId == null || providerChangeId.isBlank()) {
      throw new DomainException("Provider change ID cannot be blank");
    }
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
    providerChangeId = providerChangeId.trim();
    commitSha = commitSha.trim();
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static ChangeProposed create(
      TenantId tenantId,
      RepositoryId repositoryId,
      String providerChangeId,
      ChangeId changeId,
      String commitSha,
      AnalysisRunId analysisRunId) {
    return new ChangeProposed(
        UUID.randomUUID(), Instant.now(), tenantId, repositoryId,
        providerChangeId, changeId, commitSha, analysisRunId);
  }
}