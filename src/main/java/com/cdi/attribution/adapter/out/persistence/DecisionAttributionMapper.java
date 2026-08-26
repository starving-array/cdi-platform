package com.cdi.attribution.adapter.out.persistence;

import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.DecisionAttribution;
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

public final class DecisionAttributionMapper {

  private DecisionAttributionMapper() {}

  public static DecisionAttribution toDomain(DecisionAttributionEntity entity) {
    if (entity == null) return null;

    return new DecisionAttribution(
        new AttributionId(entity.getId()),
        new TenantId(entity.getTenantId()),
        new DeploymentId(entity.getDeploymentId()),
        new ServiceId(entity.getServiceId()),
        entity.getAnalysisRunId() != null ? new AnalysisRunId(entity.getAnalysisRunId()) : null,
        entity.getRiskAssessmentId() != null ? new RiskAssessmentId(entity.getRiskAssessmentId()) : null,
        entity.getDecisionRecordId() != null ? new DecisionId(entity.getDecisionRecordId()) : null,
        AttributionClassification.valueOf(entity.getClassification()),
        OutcomeType.valueOf(entity.getDeploymentOutcome()),
        entity.getPredictedRiskLevel() != null ? RiskLevel.valueOf(entity.getPredictedRiskLevel()) : null,
        entity.getDecisionOutcome() != null ? DecisionOutcome.valueOf(entity.getDecisionOutcome()) : null,
        entity.isHasHumanOverride(),
        entity.getAttributedAt(),
        entity.getCreatedAt());
  }

  public static DecisionAttributionEntity toEntity(DecisionAttribution domain) {
    if (domain == null) return null;

    return new DecisionAttributionEntity(
        domain.getId().value(),
        domain.getTenantId().value(),
        domain.getDeploymentId().value(),
        domain.getServiceId().value(),
        domain.getAnalysisRunId().map(AnalysisRunId::value).orElse(null),
        domain.getRiskAssessmentId().map(RiskAssessmentId::value).orElse(null),
        domain.getDecisionRecordId().map(DecisionId::value).orElse(null),
        domain.getClassification().name(),
        domain.getDeploymentOutcome().name(),
        domain.getPredictedRiskLevel().map(RiskLevel::name).orElse(null),
        domain.getDecisionOutcome().map(DecisionOutcome::name).orElse(null),
        domain.isHasHumanOverride(),
        domain.getAttributedAt(),
        domain.getCreatedAt());
  }
}