package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a Tenant/Organization.
 */
public record TenantId(UUID value) {
    public TenantId {
        if (value == null) {
            throw new DomainException("TenantId value cannot be null");
        }
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId fromString(String uuid) {
        try {
            return new TenantId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for TenantId: " + uuid, e);
        }
    }
}
