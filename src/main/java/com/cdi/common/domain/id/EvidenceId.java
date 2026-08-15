package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for an EvidenceRecord.
 */
public record EvidenceId(UUID value) {
    public EvidenceId {
        if (value == null) {
            throw new DomainException("EvidenceId value cannot be null");
        }
    }

    public static EvidenceId generate() {
        return new EvidenceId(UUID.randomUUID());
    }

    public static EvidenceId fromString(String uuid) {
        try {
            return new EvidenceId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for EvidenceId: " + uuid, e);
        }
    }
}
