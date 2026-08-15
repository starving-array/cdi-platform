package com.cdi.organization.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Organization Aggregate Root.
 * Represents an enterprise tenant in the system.
 */
public class Organization {

    public enum Status {
        ACTIVE, SUSPENDED
    }

    private final TenantId id;
    private String name;
    private Status status;
    private final Instant createdAt;

    public Organization(TenantId id, String name, Instant createdAt) {
        if (id == null) {
            throw new DomainException("Organization ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("Organization name cannot be blank");
        }
        if (createdAt == null) {
            throw new DomainException("Creation timestamp cannot be null");
        }

        this.id = id;
        this.name = name.trim();
        this.status = Status.ACTIVE;
        this.createdAt = createdAt;
    }

    public TenantId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void suspend() {
        if (this.status == Status.SUSPENDED) {
            throw new DomainException("Organization is already suspended");
        }
        this.status = Status.SUSPENDED;
    }

    public void reactivate() {
        if (this.status == Status.ACTIVE) {
            throw new DomainException("Organization is already active");
        }
        this.status = Status.ACTIVE;
    }

    /**
     * Reconstructs an Organization from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status is supplied
     * explicitly rather than defaulted by the constructor. This is a
     * persistence-read helper; it performs no state transitions.
     */
    public static Organization restore(TenantId id, String name, Status status, Instant createdAt) {
        Organization org = new Organization(id, name, createdAt);
        org.status = status;
        return org;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Organization that = (Organization) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
