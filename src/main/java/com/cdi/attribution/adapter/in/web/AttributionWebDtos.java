package com.cdi.attribution.adapter.in.web;

import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.risk.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public final class AttributionWebDtos {

  private AttributionWebDtos() {}

  public record AttributionDto(
      UUID id,
      UUID tenantId,
      UUID deploymentId,
      UUID serviceId,
      UUID analysisRunId,
      UUID riskAssessmentId,
      UUID decisionRecordId,
      AttributionClassification classification,
      OutcomeType deploymentOutcome,
      RiskLevel predictedRiskLevel,
      DecisionOutcome decisionOutcome,
      boolean hasHumanOverride,
      Instant attributedAt,
      Instant createdAt) {

    public static AttributionDto fromDomain(DecisionAttribution domain) {
      if (domain == null) return null;
      return new AttributionDto(
          domain.getId().value(),
          domain.getTenantId().value(),
          domain.getDeploymentId().value(),
          domain.getServiceId().value(),
          domain.getAnalysisRunId().map(id -> id.value()).orElse(null),
          domain.getRiskAssessmentId().map(id -> id.value()).orElse(null),
          domain.getDecisionRecordId().map(id -> id.value()).orElse(null),
          domain.getClassification(),
          domain.getDeploymentOutcome(),
          domain.getPredictedRiskLevel().orElse(null),
          domain.getDecisionOutcome().orElse(null),
          domain.isHasHumanOverride(),
          domain.getAttributedAt(),
          domain.getCreatedAt());
    }
  }

  public record AttributionSummaryDto(
      UUID tenantId,
      UUID serviceId,
      long totalAttributedDeployments,
      long accurateLowRiskCount,
      long accurateHighRiskCount,
      long underestimatedRiskCount,
      long overestimatedRiskCount,
      long unattributedCount,
      double accuracyRate) {

    public static AttributionSummaryDto fromDomain(AttributionSummary domain) {
      if (domain == null) return null;
      return new AttributionSummaryDto(
          domain.tenantId().value(),
          domain.getServiceId().map(id -> id.value()).orElse(null),
          domain.totalAttributedDeployments(),
          domain.accurateLowRiskCount(),
          domain.accurateHighRiskCount(),
          domain.underestimatedRiskCount(),
          domain.overestimatedRiskCount(),
          domain.unattributedCount(),
          domain.accuracyRate());
    }
  }
}