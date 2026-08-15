package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a specific Analysis Run on a specific commit snapshot.
 */
public record AnalysisRunId(UUID value) {
    public AnalysisRunId {
        if (value == null) {
            throw new DomainException("AnalysisRunId value cannot be null");
        }
    }

    public static AnalysisRunId generate() {
        return new AnalysisRunId(UUID.randomUUID());
    }

    public static AnalysisRunId fromString(String uuid) {
        try {
            return new AnalysisRunId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for AnalysisRunId: " + uuid, e);
        }
    }
}
