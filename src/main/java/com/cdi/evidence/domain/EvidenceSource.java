package com.cdi.evidence.domain;

import com.cdi.common.domain.exception.DomainException;

import java.util.Objects;

/**
 * Provider-neutral representation of where evidence came from.
 */
public record EvidenceSource(SourceType sourceType, String sourceReference) {
    public EvidenceSource {
        if (sourceType == null) {
            throw new DomainException("SourceType cannot be null");
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new DomainException("SourceReference cannot be null or blank");
        }
    }
}
