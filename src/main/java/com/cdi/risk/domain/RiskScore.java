package com.cdi.risk.domain;

import com.cdi.common.domain.exception.DomainException;

/**
 * A deterministic risk score bound between 0 and 100.
 */
public record RiskScore(int value) {
    public RiskScore {
        if (value < 0 || value > 100) {
            throw new DomainException("RiskScore must be between 0 and 100");
        }
    }
    
    public static RiskScore of(int value) {
        return new RiskScore(value);
    }
    
    public RiskScore add(int contribution) {
        return new RiskScore(Math.min(100, Math.max(0, this.value + contribution)));
    }
}
