package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;
import java.time.Instant;

/**
 * Value object representing a failure during an AnalysisRun.
 */
public record AnalysisFailure(FailureCategory category, String failureCode, Instant failedAt) {
    
    public enum FailureCategory {
        SOURCE_UNAVAILABLE,
        CONTEXT_UNAVAILABLE,
        EVIDENCE_UNAVAILABLE,
        ANALYSIS_FAILED
    }

    public AnalysisFailure {
        if (category == null) {
            throw new DomainException("Failure category cannot be null");
        }
        if (failedAt == null) {
            throw new DomainException("Failure timestamp cannot be null");
        }
        failureCode = failureCode != null ? failureCode.trim() : "";
    }
}
