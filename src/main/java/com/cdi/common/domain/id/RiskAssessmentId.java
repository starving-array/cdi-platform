package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a RiskAssessment.
 */
public record RiskAssessmentId(UUID value) {
    public RiskAssessmentId {
        if (value == null) {
            throw new DomainException("RiskAssessmentId value cannot be null");
        }
    }

    public static RiskAssessmentId generate() {
        return new RiskAssessmentId(UUID.randomUUID());
    }

    public static RiskAssessmentId fromString(String uuid) {
        try {
            return new RiskAssessmentId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for RiskAssessmentId: " + uuid, e);
        }
    }
}
