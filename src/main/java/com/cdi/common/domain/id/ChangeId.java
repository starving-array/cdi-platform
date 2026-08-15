package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a Change (e.g., Pull Request).
 */
public record ChangeId(UUID value) {
    public ChangeId {
        if (value == null) {
            throw new DomainException("ChangeId value cannot be null");
        }
    }

    public static ChangeId generate() {
        return new ChangeId(UUID.randomUUID());
    }

    public static ChangeId fromString(String uuid) {
        try {
            return new ChangeId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for ChangeId: " + uuid, e);
        }
    }
}
