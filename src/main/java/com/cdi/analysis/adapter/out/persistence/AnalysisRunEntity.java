package com.cdi.analysis.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code analysis_run} table (data-model.md §B).
 *
 * <p>Persistence-only mapping of the {@code AnalysisRun} aggregate: identifiers
 * and scalar state only, no domain logic, no relationships. The immutable
 * {@code CodeSnapshot} is flattened to {@code commit_sha}/{@code branch}; status
 * and the optional failure payload are stored as plain columns. The
 * {@code UNIQUE (tenant_id, change_id, commit_sha)} constraint is the final
 * idempotency safety net.
 */
@Entity
@Table(name = "analysis_run")
public class AnalysisRunEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID changeId;

  private String commitSha;

  private String branch;

  private String status;

  private String failureCategory;

  private String failureCode;

  private Instant failedAt;

  private Instant createdAt;

  private Instant completedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getChangeId() { return changeId; }

  public void setChangeId(UUID changeId) { this.changeId = changeId; }

  public String getCommitSha() { return commitSha; }

  public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

  public String getBranch() { return branch; }

  public void setBranch(String branch) { this.branch = branch; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public String getFailureCategory() { return failureCategory; }

  public void setFailureCategory(String failureCategory) { this.failureCategory = failureCategory; }

  public String getFailureCode() { return failureCode; }

  public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

  public Instant getFailedAt() { return failedAt; }

  public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getCompletedAt() { return completedAt; }

  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}