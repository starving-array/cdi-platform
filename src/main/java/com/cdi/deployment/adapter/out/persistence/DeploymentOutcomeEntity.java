package com.cdi.deployment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployment_outcome")
public class DeploymentOutcomeEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deployment_id", nullable = false)
  private DeploymentEntity deployment;

  @Column(name = "outcome", nullable = false, length = 32)
  private String outcome;

  @Column(name = "incident_reference", length = 255)
  private String incidentReference;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DeploymentOutcomeEntity() {}

  public DeploymentOutcomeEntity(
      UUID id,
      UUID tenantId,
      String outcome,
      String incidentReference,
      Instant recordedAt,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.outcome = outcome;
    this.incidentReference = incidentReference;
    this.recordedAt = recordedAt;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public DeploymentEntity getDeployment() { return deployment; }
  public void setDeployment(DeploymentEntity deployment) { this.deployment = deployment; }
  public String getOutcome() { return outcome; }
  public String getIncidentReference() { return incidentReference; }
  public Instant getRecordedAt() { return recordedAt; }
  public Instant getCreatedAt() { return createdAt; }
}