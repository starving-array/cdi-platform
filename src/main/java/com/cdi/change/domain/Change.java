package com.cdi.change.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;

import java.time.Instant;
import java.util.Objects;

/**
 * Change Aggregate Root.
 * Represents a logical software change (e.g., a Pull Request).
 * Does not contain the actual analysis, but tracks the latest commit.
 */
public class Change {

    public enum Status {
        OPEN, MERGED, CLOSED
    }

    private final ChangeId id;
    private final RepositoryId repositoryId;
    private final String providerChangeId; // e.g. "PR-42"
    
    private String title;
    private String description;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    
    private String latestCommitSha;
    private Status status;
    
    private final Instant createdAt;
    private Instant updatedAt;

    public Change(ChangeId id, RepositoryId repositoryId, String providerChangeId, 
                  String title, String description, String author, 
                  String sourceBranch, String targetBranch, String latestCommitSha, 
                  Instant createdAt) {
        
        if (id == null) throw new DomainException("Change ID cannot be null");
        if (repositoryId == null) throw new DomainException("Repository ID cannot be null");
        if (providerChangeId == null || providerChangeId.isBlank()) throw new DomainException("Provider change ID cannot be blank");
        if (latestCommitSha == null || latestCommitSha.isBlank()) throw new DomainException("Initial commit SHA cannot be blank");
        if (createdAt == null) throw new DomainException("Creation timestamp cannot be null");

        this.id = id;
        this.repositoryId = repositoryId;
        this.providerChangeId = providerChangeId.trim();
        this.title = title != null ? title.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.author = author != null ? author.trim() : "unknown";
        this.sourceBranch = sourceBranch != null ? sourceBranch.trim() : "";
        this.targetBranch = targetBranch != null ? targetBranch.trim() : "";
        
        this.latestCommitSha = latestCommitSha.trim();
        this.status = Status.OPEN;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void updateLatestCommit(String newCommitSha, Instant updateTime) {
        if (this.status != Status.OPEN) {
            throw new DomainException("Cannot update commits on a closed or merged change");
        }
        if (newCommitSha == null || newCommitSha.isBlank()) {
            throw new DomainException("New commit SHA cannot be blank");
        }
        this.latestCommitSha = newCommitSha.trim();
        this.updatedAt = updateTime;
    }

    public void merge(Instant updateTime) {
        if (this.status != Status.OPEN) {
            throw new DomainException("Only OPEN changes can be merged");
        }
        this.status = Status.MERGED;
        this.updatedAt = updateTime;
    }

    public void close(Instant updateTime) {
        if (this.status != Status.OPEN) {
            throw new DomainException("Only OPEN changes can be closed");
        }
        this.status = Status.CLOSED;
        this.updatedAt = updateTime;
    }

    public void reopen(Instant updateTime) {
        if (this.status == Status.OPEN) {
            throw new DomainException("Change is already open");
        }
        this.status = Status.OPEN;
        this.updatedAt = updateTime;
    }

    /**
     * Reconstructs a Change from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status and {@code updatedAt}
     * are supplied explicitly rather than defaulted by the constructor. This
     * is a persistence-read helper; it performs no state transitions.
     */
    public static Change restore(ChangeId id, RepositoryId repositoryId, String providerChangeId,
            String title, String description, String author,
            String sourceBranch, String targetBranch, String latestCommitSha,
            Status status, Instant createdAt, Instant updatedAt) {
        Change change = new Change(id, repositoryId, providerChangeId, title, description, author,
                sourceBranch, targetBranch, latestCommitSha, createdAt);
        change.status = status;
        change.updatedAt = updatedAt;
        return change;
    }

    // Getters
    public ChangeId getId() { return id; }
    public RepositoryId getRepositoryId() { return repositoryId; }
    public String getProviderChangeId() { return providerChangeId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getSourceBranch() { return sourceBranch; }
    public String getTargetBranch() { return targetBranch; }
    public String getLatestCommitSha() { return latestCommitSha; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Change change = (Change) o;
        return id.equals(change.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
