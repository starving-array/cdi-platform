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
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionAttributionTest {

  @Test
  void testAllFiveAttributionClassifications() {
    // 1. ACCURATE_LOW_RISK
    assertEquals(AttributionClassification.ACCURATE_LOW_RISK,
        DecisionAttribution.classify(OutcomeType.SUCCESS, RiskLevel.LOW));
    assertEquals(AttributionClassification.ACCURATE_LOW_RISK,
        DecisionAttribution.classify(OutcomeType.SUCCESS, RiskLevel.MEDIUM));

    // 2. ACCURATE_HIGH_RISK
    assertEquals(AttributionClassification.ACCURATE_HIGH_RISK,
        DecisionAttribution.classify(OutcomeType.FAILURE, RiskLevel.HIGH));
    assertEquals(AttributionClassification.ACCURATE_HIGH_RISK,
        DecisionAttribution.classify(OutcomeType.INCIDENT, RiskLevel.CRITICAL));
    assertEquals(AttributionClassification.ACCURATE_HIGH_RISK,
        DecisionAttribution.classify(OutcomeType.ROLLED_BACK, RiskLevel.HIGH));

    // 3. UNDERESTIMATED_RISK (False Negative)
    assertEquals(AttributionClassification.UNDERESTIMATED_RISK,
        DecisionAttribution.classify(OutcomeType.FAILURE, RiskLevel.LOW));
    assertEquals(AttributionClassification.UNDERESTIMATED_RISK,
        DecisionAttribution.classify(OutcomeType.INCIDENT, RiskLevel.MEDIUM));
    assertEquals(AttributionClassification.UNDERESTIMATED_RISK,
        DecisionAttribution.classify(OutcomeType.ROLLED_BACK, RiskLevel.LOW));

    // 4. OVERESTIMATED_RISK (False Positive)
    assertEquals(AttributionClassification.OVERESTIMATED_RISK,
        DecisionAttribution.classify(OutcomeType.SUCCESS, RiskLevel.HIGH));
    assertEquals(AttributionClassification.OVERESTIMATED_RISK,
        DecisionAttribution.classify(OutcomeType.SUCCESS, RiskLevel.CRITICAL));

    // 5. UNATTRIBUTED
    assertEquals(AttributionClassification.UNATTRIBUTED,
        DecisionAttribution.classify(null, RiskLevel.LOW));
    assertEquals(AttributionClassification.UNATTRIBUTED,
        DecisionAttribution.classify(OutcomeType.SUCCESS, null));
  }

  @Test
  void testDecisionAttributionEntityProperties() {
    TenantId tenantId = TenantId.generate();
    DeploymentId deploymentId = DeploymentId.generate();
    ServiceId serviceId = ServiceId.generate();
    AnalysisRunId runId = AnalysisRunId.generate();
    RiskAssessmentId riskId = RiskAssessmentId.generate();
    DecisionId decisionId = DecisionId.generate();
    Instant now = Instant.parse("2026-08-16T12:00:00Z");

    DecisionAttribution attribution = new DecisionAttribution(
        AttributionId.generate(),
        tenantId,
        deploymentId,
        serviceId,
        runId,
        riskId,
        decisionId,
        AttributionClassification.ACCURATE_LOW_RISK,
        OutcomeType.SUCCESS,
        RiskLevel.LOW,
        DecisionOutcome.APPROVE,
        false,
        now,
        now);

    assertEquals(AttributionClassification.ACCURATE_LOW_RISK, attribution.getClassification());
    assertEquals(OutcomeType.SUCCESS, attribution.getDeploymentOutcome());
    assertEquals(RiskLevel.LOW, attribution.getPredictedRiskLevel().orElse(null));
    assertEquals(DecisionOutcome.APPROVE, attribution.getDecisionOutcome().orElse(null));
    assertFalse(attribution.isHasHumanOverride());
    assertTrue(attribution.getAnalysisRunId().isPresent());
    assertTrue(attribution.getRiskAssessmentId().isPresent());
    assertTrue(attribution.getDecisionRecordId().isPresent());
  }
}