package com.cdi.risk.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.EvidenceId;

import java.util.Collections;
import java.util.List;

/**
 * Explains a specific signal that contributed to the total risk score.
 */
public record RiskFactor(
        RiskFactorType type,
        int contribution,
        String explanation,
        List<EvidenceId> evidenceReferences) {
        
    public RiskFactor {
        if (type == null) {
            throw new DomainException("RiskFactor type cannot be null");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new DomainException("Explanation cannot be blank");
        }
        if (evidenceReferences == null) {
            evidenceReferences = Collections.emptyList();
        } else {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }
}
