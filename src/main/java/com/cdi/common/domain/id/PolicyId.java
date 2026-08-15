package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a Policy.
 */
public record PolicyId(UUID value) {
    public PolicyId {
        if (value == null) {
            throw new DomainException("PolicyId value cannot be null");
        }
    }

    public static PolicyId generate() {
        return new PolicyId(UUID.randomUUID());
    }

    public static PolicyId fromString(String uuid) {
        try {
            return new PolicyId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for PolicyId: " + uuid, e);
        }
    }
}
