package com.cdi.deployment.domain;

import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DeploymentOutcome {

  private final UUID id;
  private final TenantId tenantId;
  private final DeploymentId deploymentId;
  private final OutcomeType outcome;
  private final String incidentReference;
  private final Instant recordedAt;
  private final Instant createdAt;

  public DeploymentOutcome(
      UUID id,
      TenantId tenantId,
      DeploymentId deploymentId,
      OutcomeType outcome,
      String incidentReference,
      Instant recordedAt,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.deploymentId = Objects.requireNonNull(deploymentId, "deploymentId must not be null");
    this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
    this.incidentReference = incidentReference;
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public UUID getId() { return id; }
  public TenantId getTenantId() { return tenantId; }
  public DeploymentId getDeploymentId() { return deploymentId; }
  public OutcomeType getOutcome() { return outcome; }
  public Optional<String> getIncidentReference() { return Optional.ofNullable(incidentReference); }
  public Instant getRecordedAt() { return recordedAt; }
  public Instant getCreatedAt() { return createdAt; }
}