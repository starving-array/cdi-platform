package com.cdi.decision.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.EvidenceId;

import java.util.Collections;
import java.util.List;

/**
 * Explains why the policy produced a specific decision.
 */
public record DecisionReason(
        String explanation,
        String ruleId,
        List<EvidenceId> evidenceReferences) {
        
    public DecisionReason {
        if (explanation == null || explanation.isBlank()) {
            throw new DomainException("Reason explanation cannot be blank");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new DomainException("Rule ID cannot be blank");
        }
        evidenceReferences = evidenceReferences == null ? Collections.emptyList() : List.copyOf(evidenceReferences);
    }
}
