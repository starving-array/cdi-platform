package com.cdi.risk.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA representation of the {@code risk_factor} table (data-model.md §D).
 *
 * <p>Persistence-only mapping of the {@code RiskFactor} value object:
 * deterministic signal, contribution, explanation, and any evidence
 * references. The {@code List<EvidenceId>} is flattened to a comma-separated
 * UUID string because it is a never-query-by-column child payload
 * (data-model.md §7); no domain logic. Owned by its parent
 * {@code RiskAssessmentEntity} via a cascade (aggregate = assessment +
 * factors, data-model.md §7). The parent association is the owning side of
 * {@code risk_assessment_id} so Hibernate inserts it in the child row.
 */
@Entity
@Table(name = "risk_factor")
public class RiskFactorEntity {

  @Id
  private java.util.UUID id;

  private java.util.UUID tenantId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "risk_assessment_id", nullable = false)
  private RiskAssessmentEntity riskAssessment;

  private String factorType;

  private int contribution;

  private String explanation;

  private String evidenceReferenceIds;

  public java.util.UUID getId() { return id; }

  public void setId(java.util.UUID id) { this.id = id; }

  public java.util.UUID getTenantId() { return tenantId; }

  public void setTenantId(java.util.UUID tenantId) { this.tenantId = tenantId; }

  public RiskAssessmentEntity getRiskAssessment() { return riskAssessment; }

  public void setRiskAssessment(RiskAssessmentEntity riskAssessment) { this.riskAssessment = riskAssessment; }

  public String getFactorType() { return factorType; }

  public void setFactorType(String factorType) { this.factorType = factorType; }

  public int getContribution() { return contribution; }

  public void setContribution(int contribution) { this.contribution = contribution; }

  public String getExplanation() { return explanation; }

  public void setExplanation(String explanation) { this.explanation = explanation; }

  public String getEvidenceReferenceIds() { return evidenceReferenceIds; }

  public void setEvidenceReferenceIds(String evidenceReferenceIds) { this.evidenceReferenceIds = evidenceReferenceIds; }
}