package com.cdi.attribution.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decision_attribution")
public class DecisionAttributionEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "deployment_id", nullable = false)
  private UUID deploymentId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "analysis_run_id")
  private UUID analysisRunId;

  @Column(name = "risk_assessment_id")
  private UUID riskAssessmentId;

  @Column(name = "decision_record_id")
  private UUID decisionRecordId;

  @Column(name = "classification", nullable = false, length = 32)
  private String classification;

  @Column(name = "deployment_outcome", nullable = false, length = 32)
  private String deploymentOutcome;

  @Column(name = "predicted_risk_level", length = 16)
  private String predictedRiskLevel;

  @Column(name = "decision_outcome", length = 32)
  private String decisionOutcome;

  @Column(name = "has_human_override", nullable = false)
  private boolean hasHumanOverride;

  @Column(name = "attributed_at", nullable = false)
  private Instant attributedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DecisionAttributionEntity() {}

  public DecisionAttributionEntity(
      UUID id,
      UUID tenantId,
      UUID deploymentId,
      UUID serviceId,
      UUID analysisRunId,
      UUID riskAssessmentId,
      UUID decisionRecordId,
      String classification,
      String deploymentOutcome,
      String predictedRiskLevel,
      String decisionOutcome,
      boolean hasHumanOverride,
      Instant attributedAt,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.deploymentId = deploymentId;
    this.serviceId = serviceId;
    this.analysisRunId = analysisRunId;
    this.riskAssessmentId = riskAssessmentId;
    this.decisionRecordId = decisionRecordId;
    this.classification = classification;
    this.deploymentOutcome = deploymentOutcome;
    this.predictedRiskLevel = predictedRiskLevel;
    this.decisionOutcome = decisionOutcome;
    this.hasHumanOverride = hasHumanOverride;
    this.attributedAt = attributedAt;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getDeploymentId() { return deploymentId; }
  public UUID getServiceId() { return serviceId; }
  public UUID getAnalysisRunId() { return analysisRunId; }
  public UUID getRiskAssessmentId() { return riskAssessmentId; }
  public UUID getDecisionRecordId() { return decisionRecordId; }
  public String getClassification() { return classification; }
  public String getDeploymentOutcome() { return deploymentOutcome; }
  public String getPredictedRiskLevel() { return predictedRiskLevel; }
  public String getDecisionOutcome() { return decisionOutcome; }
  public boolean isHasHumanOverride() { return hasHumanOverride; }
  public Instant getAttributedAt() { return attributedAt; }
  public Instant getCreatedAt() { return createdAt; }
}