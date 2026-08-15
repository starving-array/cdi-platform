package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a DecisionRecord.
 */
public record DecisionId(UUID value) {
    public DecisionId {
        if (value == null) {
            throw new DomainException("DecisionId value cannot be null");
        }
    }

    public static DecisionId generate() {
        return new DecisionId(UUID.randomUUID());
    }

    public static DecisionId fromString(String uuid) {
        try {
            return new DecisionId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for DecisionId: " + uuid, e);
        }
    }
}
