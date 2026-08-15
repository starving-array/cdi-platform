package com.cdi.risk.adapter.out.persistence;

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
 * JPA representation of the {@code risk_assessment} table (data-model.md §D).
 *
 * <p>Persistence-only mapping of the {@code RiskAssessment} aggregate:
 * identifiers and scalar immutable state only, no domain logic. Risk factors
 * are owned 1:N children in the {@code risk_factor} table (data-model.md §7)
 * and are saved/loaded together with their parent via cascade. Scoring is
 * stamped with the risk rule set version (risk-engine.md §6) and the
 * EvidenceState records degraded retrieval (risk-engine.md §8).
 * {@code UNIQUE (tenant_id, analysis_run_id)} guarantees one assessment per
 * analysis run.
 */
@Entity
@Table(name = "risk_assessment")
public class RiskAssessmentEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID analysisRunId;

  private int riskScore;

  private String riskLevel;

  private String evidenceState;

  private String assessmentVersion;

  private Instant calculatedAt;

  @OneToMany(mappedBy = "riskAssessment", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER)
  private List<RiskFactorEntity> factors = new ArrayList<>();

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getAnalysisRunId() { return analysisRunId; }

  public void setAnalysisRunId(UUID analysisRunId) { this.analysisRunId = analysisRunId; }

  public int getRiskScore() { return riskScore; }

  public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

  public String getRiskLevel() { return riskLevel; }

  public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

  public String getEvidenceState() { return evidenceState; }

  public void setEvidenceState(String evidenceState) { this.evidenceState = evidenceState; }

  public String getAssessmentVersion() { return assessmentVersion; }

  public void setAssessmentVersion(String assessmentVersion) { this.assessmentVersion = assessmentVersion; }

  public Instant getCalculatedAt() { return calculatedAt; }

  public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

  public List<RiskFactorEntity> getFactors() { return factors; }

  public void setFactors(List<RiskFactorEntity> factors) { this.factors = factors; }
}