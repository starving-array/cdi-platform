package com.cdi.risk.domain;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic domain service for calculating engineering risk.
 */
public class DeterministicRiskEngine {
    
    public static final String RULE_VERSION = "v1.0.0";
    
    // Configurable/hardcoded for V1 rules
    private static final int BASE_SCORE = 10;
    
    public RiskAssessment assess(AnalysisRunId runId, RiskAssessmentInput input, Instant calculatedAt) {
        List<RiskFactor> factors = new ArrayList<>();
        RiskScore score = RiskScore.of(BASE_SCORE);

        // 1. Service Criticality
        if (input.serviceCriticality() == CriticalityTier.TIER_0) {
            factors.add(new RiskFactor(RiskFactorType.HIGH_SERVICE_CRITICALITY, 30, "Change affects a Tier-0 (Mission Critical) service.", null));
            score = score.add(30);
        } else if (input.serviceCriticality() == CriticalityTier.TIER_1) {
            factors.add(new RiskFactor(RiskFactorType.HIGH_SERVICE_CRITICALITY, 15, "Change affects a Tier-1 (Highly Important) service.", null));
            score = score.add(15);
        }

        // 2. Database Schema Change
        if (input.isDatabaseMigration()) {
            factors.add(new RiskFactor(RiskFactorType.DATABASE_SCHEMA_CHANGE, 25, "Database migration detected.", null));
            score = score.add(25);
        }

        // 3. Dependency Impact
        if (input.downstreamDependencyCount() > 5) {
            factors.add(new RiskFactor(RiskFactorType.HIGH_DEPENDENCY_IMPACT, 15, "Service has " + input.downstreamDependencyCount() + " downstream dependencies.", null));
            score = score.add(15);
        }

        // 4. Configuration Change
        if (input.isConfigurationChange()) {
            factors.add(new RiskFactor(RiskFactorType.CONFIGURATION_CHANGE, 10, "Configuration change detected.", null));
            score = score.add(10);
        }

        // 5. Change Size
        if (input.changedFilesCount() > 20) {
            factors.add(new RiskFactor(RiskFactorType.HIGH_CHANGE_SIZE, 10, "Large change size: " + input.changedFilesCount() + " files modified.", null));
            score = score.add(10);
        }

        // 6. Historical Incident Match
        if (!input.matchedIncidentEvidenceIds().isEmpty()) {
            factors.add(new RiskFactor(RiskFactorType.HISTORICAL_INCIDENT_MATCH, 20, "Matched " + input.matchedIncidentEvidenceIds().size() + " similar historical incidents.", input.matchedIncidentEvidenceIds()));
            score = score.add(20);
        }

        RiskLevel level = mapScoreToLevel(score);

        return new RiskAssessment(
                RiskAssessmentId.generate(),
                runId,
                score,
                level,
                factors,
                input.evidenceState(),
                RULE_VERSION,
                calculatedAt
        );
    }

    private RiskLevel mapScoreToLevel(RiskScore score) {
        int v = score.value();
        if (v < 40) return RiskLevel.LOW;
        if (v < 60) return RiskLevel.MEDIUM;
        if (v < 80) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }
}
