package com.cdi.policy.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A deterministic policy rule evaluating objective context.
 */
public record PolicyRule(
        String ruleId,
        Set<CriticalityTier> targetTiers,
        Set<RiskLevel> targetRiskLevels,
        Set<EvidenceState> requiredEvidenceStates,
        DecisionOutcome outcome,
        List<RequiredAction> actions,
        String explanation) {
        
    public PolicyRule {
        if (ruleId == null || ruleId.isBlank()) {
            throw new DomainException("Rule ID cannot be blank");
        }
        if (outcome == null) {
            throw new DomainException("Outcome cannot be null");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new DomainException("Explanation cannot be blank");
        }
        
        targetTiers = targetTiers == null ? Collections.emptySet() : Set.copyOf(targetTiers);
        targetRiskLevels = targetRiskLevels == null ? Collections.emptySet() : Set.copyOf(targetRiskLevels);
        requiredEvidenceStates = requiredEvidenceStates == null ? Collections.emptySet() : Set.copyOf(requiredEvidenceStates);
        actions = actions == null ? Collections.emptyList() : List.copyOf(actions);
    }
    
    public boolean matches(CriticalityTier tier, RiskLevel riskLevel, EvidenceState evidenceState) {
        if (!targetTiers.isEmpty() && !targetTiers.contains(tier)) return false;
        if (!targetRiskLevels.isEmpty() && !targetRiskLevels.contains(riskLevel)) return false;
        if (!requiredEvidenceStates.isEmpty() && !requiredEvidenceStates.contains(evidenceState)) return false;
        return true;
    }
}
