package com.cdi.decision.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA representation of the {@code decision_reason} table (data-model.md §7).
 *
 * <p>Owned 1:N child of {@code DecisionRecordEntity}: each reason traces the
 * evaluation back to the explicit {@code ruleId} and any {@code EvidenceId}
 * references (flattened to a comma-separated UUID list — children are never
 * queried by that column). Persistence-only; no domain logic.
 */
@Entity
@Table(name = "decision_reason")
public class DecisionReasonEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decision_record_id")
  private DecisionRecordEntity decisionRecord;

  private String explanation;

  private String ruleId;

  private String evidenceReferenceIds;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public DecisionRecordEntity getDecisionRecord() { return decisionRecord; }

  public void setDecisionRecord(DecisionRecordEntity decisionRecord) { this.decisionRecord = decisionRecord; }

  public String getExplanation() { return explanation; }

  public void setExplanation(String explanation) { this.explanation = explanation; }

  public String getRuleId() { return ruleId; }

  public void setRuleId(String ruleId) { this.ruleId = ruleId; }

  public String getEvidenceReferenceIds() { return evidenceReferenceIds; }

  public void setEvidenceReferenceIds(String evidenceReferenceIds) { this.evidenceReferenceIds = evidenceReferenceIds; }
}
