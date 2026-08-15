package com.cdi.policy.domain;

import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic engine that maps Risk inputs and Policy rules to a Decision.
 */
public class PolicyEngine {

    public DecisionRecord evaluate(Policy policy, RiskAssessment riskAssessment, CriticalityTier tier, Instant generatedAt) {
        List<PolicyRule> matchingRules = new ArrayList<>();
        
        // Find all matching rules
        for (PolicyRule rule : policy.getRules()) {
            if (rule.matches(tier, riskAssessment.getLevel(), riskAssessment.getEvidenceState())) {
                matchingRules.add(rule);
            }
        }
        
        // Resolve outcome based on precedence: BLOCK > REVIEW_REQUIRED > APPROVE
        DecisionOutcome finalOutcome = DecisionOutcome.APPROVE; // default fallback if needed
        List<DecisionReason> reasons = new ArrayList<>();
        List<RequiredAction> finalActions = new ArrayList<>();
        
        if (matchingRules.isEmpty()) {
            reasons.add(new DecisionReason("No explicit policy rule matched. Defaulting to REVIEW_REQUIRED for safety.", "DEFAULT_FALLBACK", List.of()));
            finalOutcome = DecisionOutcome.REVIEW_REQUIRED;
        } else {
            for (PolicyRule rule : matchingRules) {
                if (isHigherPrecedence(rule.outcome(), finalOutcome)) {
                    finalOutcome = rule.outcome();
                }
                reasons.add(new DecisionReason(rule.explanation(), rule.ruleId(), List.of()));
                
                // Collect required actions
                for (RequiredAction action : rule.actions()) {
                    if (!finalActions.contains(action)) {
                        finalActions.add(action);
                    }
                }
            }
        }
        
        return DecisionRecord.builder()
                .tenantId(policy.getTenantId())
                .analysisRunId(riskAssessment.getAnalysisRunId())
                .riskAssessmentId(riskAssessment.getId())
                .policyId(policy.getId())
                .policyVersion(policy.getVersion().value())
                .outcome(finalOutcome)
                .reasons(reasons)
                .requiredActions(finalActions)
                .generatedAt(generatedAt)
                .build();
    }
    
    private boolean isHigherPrecedence(DecisionOutcome newOutcome, DecisionOutcome currentOutcome) {
        if (newOutcome == DecisionOutcome.BLOCK) return true;
        if (newOutcome == DecisionOutcome.REVIEW_REQUIRED && currentOutcome != DecisionOutcome.BLOCK) return true;
        return false;
    }
}
