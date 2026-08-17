package com.cdi.decision.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA representation of the {@code decision_record} table (data-model.md §E).
 *
 * <p>Persistence-only mapping of the {@code DecisionRecord} aggregate:
 * identifiers and scalar immutable state only, no domain logic. Decision
 * reasons are owned 1:N children in the {@code decision_reason} table
 * (data-model.md §7) and are saved/loaded together with their parent via
 * cascade; required actions are a small fixed set of enums flattened to a CSV
 * column (never-query-by-column child payload, data-model.md §7).
 * {@code UNIQUE (tenant_id, analysis_run_id)} guarantees one decision per
 * analysis run (application-layer.md §10).
 *
 * <p>An aggregate may carry an optional {@link HumanOverrideEntity} (1:1,
 * {@code human_override} table, data-model.md §E/§11, ADR-006). The override
 * is supplemental: it records the one immutable human change of the decision's
 * outcome (UC-06) without ever modifying this parent's own outcome columns.
 */
@Entity
@Table(name = "decision_record")
public class DecisionRecordEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID analysisRunId;

  private UUID riskAssessmentId;

  private UUID policyId;

  private String policyVersion;

  private String outcome;

  private String requiredActions;

  private Instant generatedAt;

  @OneToMany(mappedBy = "decisionRecord", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER)
  private List<DecisionReasonEntity> reasons = new ArrayList<>();

  @OneToOne(mappedBy = "decisionRecord", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER)
  private HumanOverrideEntity override;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getAnalysisRunId() { return analysisRunId; }

  public void setAnalysisRunId(UUID analysisRunId) { this.analysisRunId = analysisRunId; }

  public UUID getRiskAssessmentId() { return riskAssessmentId; }

  public void setRiskAssessmentId(UUID riskAssessmentId) { this.riskAssessmentId = riskAssessmentId; }

  public UUID getPolicyId() { return policyId; }

  public void setPolicyId(UUID policyId) { this.policyId = policyId; }

  public String getPolicyVersion() { return policyVersion; }

  public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

  public String getOutcome() { return outcome; }

  public void setOutcome(String outcome) { this.outcome = outcome; }

  public String getRequiredActions() { return requiredActions; }

  public void setRequiredActions(String requiredActions) { this.requiredActions = requiredActions; }

  public Instant getGeneratedAt() { return generatedAt; }

  public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

  public List<DecisionReasonEntity> getReasons() { return reasons; }

  public void setReasons(List<DecisionReasonEntity> reasons) { this.reasons = reasons; }

  public HumanOverrideEntity getOverride() { return override; }

  public void setOverride(HumanOverrideEntity override) { this.override = override; }
}
