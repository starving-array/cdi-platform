package com.cdi.evidence.domain;

import com.cdi.common.domain.exception.DomainException;

/**
 * Indicates how useful this evidence is to the current analysis.
 */
public record Relevance(Double score, String reason) {
    public Relevance {
        if (score != null && (score < 0.0 || score > 1.0)) {
            throw new DomainException("Relevance score must be between 0.0 and 1.0");
        }
    }

    public static Relevance of(Double score, String reason) {
        return new Relevance(score, reason);
    }
}
