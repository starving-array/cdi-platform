package com.cdi.investigation.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA representation of the {@code agent_investigation} table (data-model.md §D).
 *
 * <p>Persistence-only mapping of the {@code AgentInvestigation} aggregate:
 * identifiers and scalar immutable state only, no domain logic. Findings are
 * owned 1:N children in the {@code investigation_finding} table, each with a
 * cross-reference of {@code finding_evidence} citations, saved/loaded together
 * via cascade. {@code UNIQUE (tenant_id, analysis_run_id)} guarantees one
 * investigation per analysis run.
 */
@Entity
@Table(name = "agent_investigation")
public class AgentInvestigationEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID analysisRunId;

  private UUID changeId;

  private UUID riskAssessmentId;

  private String status;

  private String failureCategory;

  private String failureCode;

  private Instant createdAt;

  private Instant completedAt;

  @OneToMany(mappedBy = "agentInvestigation", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER)
  private List<InvestigationFindingEntity> findings = new ArrayList<>();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getAnalysisRunId() { return analysisRunId; }
  public void setAnalysisRunId(UUID analysisRunId) { this.analysisRunId = analysisRunId; }

  public UUID getChangeId() { return changeId; }
  public void setChangeId(UUID changeId) { this.changeId = changeId; }

  public UUID getRiskAssessmentId() { return riskAssessmentId; }
  public void setRiskAssessmentId(UUID riskAssessmentId) { this.riskAssessmentId = riskAssessmentId; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getFailureCategory() { return failureCategory; }
  public void setFailureCategory(String failureCategory) { this.failureCategory = failureCategory; }

  public String getFailureCode() { return failureCode; }
  public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

  public List<InvestigationFindingEntity> getFindings() { return findings; }
  public void setFindings(List<InvestigationFindingEntity> findings) { this.findings = findings; }
}
