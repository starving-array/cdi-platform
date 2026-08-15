package com.cdi.systemcontext.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Service Aggregate Root (System Context).
 * Represents a deployable or business-relevant software service.
 */
public class Service {

    public enum Status {
        ACTIVE, DEPRECATED
    }

    private final ServiceId id;
    private final TenantId tenantId;
    private RepositoryId repositoryId; // Optional association
    private String name;
    private CriticalityTier criticality;
    private String owner;
    private Status status;
    private final Instant createdAt;
    private final Set<ServiceDependency> dependencies;

    public Service(ServiceId id, TenantId tenantId, String name, CriticalityTier criticality, String owner, Instant createdAt) {
        if (id == null) throw new DomainException("Service ID cannot be null");
        if (tenantId == null) throw new DomainException("Tenant ID cannot be null");
        if (name == null || name.isBlank()) throw new DomainException("Service name cannot be blank");
        if (criticality == null) throw new DomainException("Criticality cannot be null");
        if (createdAt == null) throw new DomainException("Creation timestamp cannot be null");

        this.id = id;
        this.tenantId = tenantId;
        this.name = name.trim();
        this.criticality = criticality;
        this.owner = owner != null ? owner.trim() : null;
        this.status = Status.ACTIVE;
        this.createdAt = createdAt;
        this.dependencies = new HashSet<>();
    }

    public void associateWithRepository(RepositoryId repoId) {
        this.repositoryId = repoId;
    }

    public void addDependency(Service targetService) {
        if (targetService == null) {
            throw new DomainException("Target service cannot be null");
        }
        if (!this.tenantId.equals(targetService.getTenantId())) {
            throw new DomainException("Cannot depend on a service from a different organization");
        }
        if (this.id.equals(targetService.getId())) {
            throw new DomainException("A service cannot depend on itself");
        }
        this.dependencies.add(new ServiceDependency(targetService.getId()));
    }

    public void deprecate() {
        if (this.status == Status.DEPRECATED) {
            throw new DomainException("Service is already deprecated");
        }
        this.status = Status.DEPRECATED;
    }

    /**
     * Reconstructs a Service from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status is supplied
     * explicitly rather than defaulted by the constructor. This is a
     * persistence-read helper; it performs no state transitions and does
     * not alter existing constructor or {@link #deprecate()} semantics.
     */
    public static Service restore(ServiceId id, TenantId tenantId, String name,
                                   CriticalityTier criticality, String owner, Status status,
                                   Instant createdAt) {
        Service service = new Service(id, tenantId, name, criticality, owner, createdAt);
        service.status = status;
        return service;
    }

    public ServiceId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public RepositoryId getRepositoryId() { return repositoryId; }
    public String getName() { return name; }
    public CriticalityTier getCriticality() { return criticality; }
    public String getOwner() { return owner; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<ServiceDependency> getDependencies() { 
        return Collections.unmodifiableSet(dependencies); 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return id.equals(service.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
