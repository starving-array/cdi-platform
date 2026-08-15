package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a Software Service/System Context.
 */
public record ServiceId(UUID value) {
    public ServiceId {
        if (value == null) {
            throw new DomainException("ServiceId value cannot be null");
        }
    }

    public static ServiceId generate() {
        return new ServiceId(UUID.randomUUID());
    }

    public static ServiceId fromString(String uuid) {
        try {
            return new ServiceId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for ServiceId: " + uuid, e);
        }
    }
}
