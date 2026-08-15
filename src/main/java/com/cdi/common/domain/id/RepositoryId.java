package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for a source-control Repository.
 */
public record RepositoryId(UUID value) {
    public RepositoryId {
        if (value == null) {
            throw new DomainException("RepositoryId value cannot be null");
        }
    }

    public static RepositoryId generate() {
        return new RepositoryId(UUID.randomUUID());
    }

    public static RepositoryId fromString(String uuid) {
        try {
            return new RepositoryId(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid UUID format for RepositoryId: " + uuid, e);
        }
    }
}
