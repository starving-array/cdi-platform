package com.cdi.change.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code change} table (data-model.md §B).
 *
 * <p>Persistence-only mapping of the {@code Change} aggregate: identifiers and
 * scalar state only, no domain logic, no relationships. Status is stored as the
 * enum name. Column names follow the Spring camelCase-to-snake_case strategy.
 */
@Entity
@Table(name = "change")
public class ChangeEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID repositoryId;

  private String providerChangeId;

  private String title;

  private String description;

  private String author;

  private String sourceBranch;

  private String targetBranch;

  private String latestCommitSha;

  private String status;

  private Instant createdAt;

  private Instant updatedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getRepositoryId() { return repositoryId; }

  public void setRepositoryId(UUID repositoryId) { this.repositoryId = repositoryId; }

  public String getProviderChangeId() { return providerChangeId; }

  public void setProviderChangeId(String providerChangeId) { this.providerChangeId = providerChangeId; }

  public String getTitle() { return title; }

  public void setTitle(String title) { this.title = title; }

  public String getDescription() { return description; }

  public void setDescription(String description) { this.description = description; }

  public String getAuthor() { return author; }

  public void setAuthor(String author) { this.author = author; }

  public String getSourceBranch() { return sourceBranch; }

  public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

  public String getTargetBranch() { return targetBranch; }

  public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }

  public String getLatestCommitSha() { return latestCommitSha; }

  public void setLatestCommitSha(String latestCommitSha) { this.latestCommitSha = latestCommitSha; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}