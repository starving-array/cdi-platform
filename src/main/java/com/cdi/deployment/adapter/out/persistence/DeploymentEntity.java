package com.cdi.deployment.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployment")
public class DeploymentEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "commit_sha", nullable = false, length = 64)
  private String commitSha;

  @Column(name = "environment", nullable = false, length = 32)
  private String environment;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "external_deployment_id", length = 128)
  private String externalDeploymentId;

  @Column(name = "deployed_at", nullable = false)
  private Instant deployedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToOne(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true)
  private DeploymentOutcomeEntity outcome;

  protected DeploymentEntity() {}

  public DeploymentEntity(
      UUID id,
      UUID tenantId,
      UUID serviceId,
      String commitSha,
      String environment,
      String status,
      String externalDeploymentId,
      Instant deployedAt,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.serviceId = serviceId;
    this.commitSha = commitSha;
    this.environment = environment;
    this.status = status;
    this.externalDeploymentId = externalDeploymentId;
    this.deployedAt = deployedAt;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getServiceId() { return serviceId; }
  public String getCommitSha() { return commitSha; }
  public String getEnvironment() { return environment; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getExternalDeploymentId() { return externalDeploymentId; }
  public Instant getDeployedAt() { return deployedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public DeploymentOutcomeEntity getOutcome() { return outcome; }
  public void setOutcome(DeploymentOutcomeEntity outcome) {
    this.outcome = outcome;
    if (outcome != null) {
      outcome.setDeployment(this);
    }
  }
}