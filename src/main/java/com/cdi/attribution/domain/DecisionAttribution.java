package com.cdi.attribution.domain;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.AttributionId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.risk.domain.RiskLevel;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class DecisionAttribution {

  private final AttributionId id;
  private final TenantId tenantId;
  private final DeploymentId deploymentId;
  private final ServiceId serviceId;
  private final AnalysisRunId analysisRunId;
  private final RiskAssessmentId riskAssessmentId;
  private final DecisionId decisionRecordId;
  private final AttributionClassification classification;
  private final OutcomeType deploymentOutcome;
  private final RiskLevel predictedRiskLevel;
  private final DecisionOutcome decisionOutcome;
  private final boolean hasHumanOverride;
  private final Instant attributedAt;
  private final Instant createdAt;

  public DecisionAttribution(
      AttributionId id,
      TenantId tenantId,
      DeploymentId deploymentId,
      ServiceId serviceId,
      AnalysisRunId analysisRunId,
      RiskAssessmentId riskAssessmentId,
      DecisionId decisionRecordId,
      AttributionClassification classification,
      OutcomeType deploymentOutcome,
      RiskLevel predictedRiskLevel,
      DecisionOutcome decisionOutcome,
      boolean hasHumanOverride,
      Instant attributedAt,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.deploymentId = Objects.requireNonNull(deploymentId, "deploymentId must not be null");
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId must not be null");
    this.analysisRunId = analysisRunId;
    this.riskAssessmentId = riskAssessmentId;
    this.decisionRecordId = decisionRecordId;
    this.classification = Objects.requireNonNull(classification, "classification must not be null");
    this.deploymentOutcome = Objects.requireNonNull(deploymentOutcome, "deploymentOutcome must not be null");
    this.predictedRiskLevel = predictedRiskLevel;
    this.decisionOutcome = decisionOutcome;
    this.hasHumanOverride = hasHumanOverride;
    this.attributedAt = Objects.requireNonNull(attributedAt, "attributedAt must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public static AttributionClassification classify(
      OutcomeType outcome,
      RiskLevel riskLevel) {
    if (outcome == null || riskLevel == null) {
      return AttributionClassification.UNATTRIBUTED;
    }

    boolean isOutcomeSuccess = (outcome == OutcomeType.SUCCESS);
    boolean isLowOrMediumRisk = (riskLevel == RiskLevel.LOW || riskLevel == RiskLevel.MEDIUM);
    boolean isHighOrCriticalRisk = (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL);

    if (isOutcomeSuccess) {
      return isLowOrMediumRisk
          ? AttributionClassification.ACCURATE_LOW_RISK
          : AttributionClassification.OVERESTIMATED_RISK;
    } else {
      // Failure / Incident / Rollback
      return isHighOrCriticalRisk
          ? AttributionClassification.ACCURATE_HIGH_RISK
          : AttributionClassification.UNDERESTIMATED_RISK;
    }
  }

  public AttributionId getId() { return id; }
  public TenantId getTenantId() { return tenantId; }
  public DeploymentId getDeploymentId() { return deploymentId; }
  public ServiceId getServiceId() { return serviceId; }
  public Optional<AnalysisRunId> getAnalysisRunId() { return Optional.ofNullable(analysisRunId); }
  public Optional<RiskAssessmentId> getRiskAssessmentId() { return Optional.ofNullable(riskAssessmentId); }
  public Optional<DecisionId> getDecisionRecordId() { return Optional.ofNullable(decisionRecordId); }
  public AttributionClassification getClassification() { return classification; }
  public OutcomeType getDeploymentOutcome() { return deploymentOutcome; }
  public Optional<RiskLevel> getPredictedRiskLevel() { return Optional.ofNullable(predictedRiskLevel); }
  public Optional<DecisionOutcome> getDecisionOutcome() { return Optional.ofNullable(decisionOutcome); }
  public boolean isHasHumanOverride() { return hasHumanOverride; }
  public Instant getAttributedAt() { return attributedAt; }
  public Instant getCreatedAt() { return createdAt; }
}