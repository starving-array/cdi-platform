package com.cdi.repository.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Repository Aggregate Root.
 * Represents a source-control repository belonging to an organization.
 */
public class Repository {

    public enum ProviderType {
        GITHUB, GITLAB, BITBUCKET
    }

    public enum Status {
        ACTIVE, ARCHIVED
    }

    private final RepositoryId id;
    private final TenantId tenantId;
    private final ProviderType providerType;
    private final String externalId;
    private String name;
    private String url;
    private String defaultBranch;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Repository(RepositoryId id, TenantId tenantId, ProviderType providerType, 
                      String externalId, String name, String url, String defaultBranch, Instant createdAt) {
        if (id == null) throw new DomainException("Repository ID cannot be null");
        if (tenantId == null) throw new DomainException("Tenant ID cannot be null");
        if (providerType == null) throw new DomainException("Provider type cannot be null");
        if (externalId == null || externalId.isBlank()) throw new DomainException("External ID cannot be blank");
        if (name == null || name.isBlank()) throw new DomainException("Repository name cannot be blank");
        if (url == null || url.isBlank()) throw new DomainException("Repository URL cannot be blank");
        if (defaultBranch == null || defaultBranch.isBlank()) throw new DomainException("Default branch cannot be blank");
        if (createdAt == null) throw new DomainException("Creation timestamp cannot be null");

        this.id = id;
        this.tenantId = tenantId;
        this.providerType = providerType;
        this.externalId = externalId.trim();
        this.name = name.trim();
        this.url = url.trim();
        this.defaultBranch = defaultBranch.trim();
        this.status = Status.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Reconstructs a Repository from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status and {@code updatedAt}
     * are supplied explicitly rather than defaulted by the constructor. This
     * is a persistence-read helper; it performs no state transitions and does
     * not alter existing constructor or {@link #archive()} semantics.
     */
    public static Repository restore(RepositoryId id, TenantId tenantId, ProviderType providerType,
                                     String externalId, String name, String url, String defaultBranch,
                                     Status status, Instant createdAt, Instant updatedAt) {
        Repository repository = new Repository(id, tenantId, providerType, externalId, name,
                url, defaultBranch, createdAt);
        repository.status = status;
        repository.updatedAt = updatedAt;
        return repository;
    }

    public RepositoryId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public ProviderType getProviderType() { return providerType; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getDefaultBranch() { return defaultBranch; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void archive() {
        if (this.status == Status.ARCHIVED) {
            throw new DomainException("Repository is already archived");
        }
        this.status = Status.ARCHIVED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Repository that = (Repository) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
