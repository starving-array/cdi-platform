package com.cdi.decision.domain;

import com.cdi.common.domain.exception.DomainException;

import java.time.Instant;

/**
 * Captures a manual override of an automated decision.
 */
public record HumanOverride(
        String actorId,
        DecisionOutcome originalOutcome,
        DecisionOutcome newOutcome,
        String justification,
        Instant timestamp) {
        
    public HumanOverride {
        if (actorId == null || actorId.isBlank()) {
            throw new DomainException("Actor ID cannot be blank");
        }
        if (originalOutcome == null || newOutcome == null) {
            throw new DomainException("Outcomes cannot be null");
        }
        if (justification == null || justification.isBlank()) {
            throw new DomainException("Justification cannot be blank");
        }
        if (timestamp == null) {
            throw new DomainException("Timestamp cannot be null");
        }
    }
}
