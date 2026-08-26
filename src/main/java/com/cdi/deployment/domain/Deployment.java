package com.cdi.deployment.domain;

import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Deployment {

  private final DeploymentId id;
  private final TenantId tenantId;
  private final ServiceId serviceId;
  private final String commitSha;
  private final String environment;
  private DeploymentStatus status;
  private final String externalDeploymentId;
  private final Instant deployedAt;
  private final Instant createdAt;
  private DeploymentOutcome outcome;

  public Deployment(
      DeploymentId id,
      TenantId tenantId,
      ServiceId serviceId,
      String commitSha,
      String environment,
      DeploymentStatus status,
      String externalDeploymentId,
      Instant deployedAt,
      Instant createdAt) {
    this(id, tenantId, serviceId, commitSha, environment, status, externalDeploymentId, deployedAt, createdAt, null);
  }

  public Deployment(
      DeploymentId id,
      TenantId tenantId,
      ServiceId serviceId,
      String commitSha,
      String environment,
      DeploymentStatus status,
      String externalDeploymentId,
      Instant deployedAt,
      Instant createdAt,
      DeploymentOutcome outcome) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.commitSha = Objects.requireNonNull(commitSha, "commitSha must not be null");
    this.environment = Objects.requireNonNull(environment, "environment must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.externalDeploymentId = externalDeploymentId;
    this.deployedAt = Objects.requireNonNull(deployedAt, "deployedAt must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.outcome = outcome;
  }

  public void recordOutcome(OutcomeType outcomeType, String incidentReference, Instant recordedAt, Instant now) {
    if (this.outcome != null) {
      throw new IllegalStateException("Deployment already has a recorded outcome");
    }
    this.outcome = new DeploymentOutcome(
        java.util.UUID.randomUUID(),
        this.tenantId,
        this.id,
        outcomeType,
        incidentReference,
        recordedAt,
        now);

    // Update lifecycle status based on recorded outcome
    switch (outcomeType) {
      case SUCCESS -> this.status = DeploymentStatus.SUCCESSFUL;
      case FAILURE -> this.status = DeploymentStatus.FAILED;
      case INCIDENT -> this.status = DeploymentStatus.FAILED;
      case ROLLED_BACK -> this.status = DeploymentStatus.ROLLED_BACK;
    }
  }

  public DeploymentId getId() { return id; }
  public TenantId getTenantId() { return tenantId; }
  public ServiceId getServiceId() { return serviceId; }
  public String getCommitSha() { return commitSha; }
  public String getEnvironment() { return environment; }
  public DeploymentStatus getStatus() { return status; }
  public Optional<String> getExternalDeploymentId() { return Optional.ofNullable(externalDeploymentId); }
  public Instant getDeployedAt() { return deployedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Optional<DeploymentOutcome> getOutcome() { return Optional.ofNullable(outcome); }
}