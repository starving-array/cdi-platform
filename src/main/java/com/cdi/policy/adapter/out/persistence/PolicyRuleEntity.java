package com.cdi.policy.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA representation of the {@code policy_rule} table (data-model.md §E, V9
 * migration).
 *
 * <p>Persistence-only mapping of the {@code PolicyRule} value object: the
 * deterministic match sets ({@code targetTiers}, {@code targetRiskLevels},
 * {@code requiredEvidenceStates}), the {@code outcome}, the
 * {@code requiredActions} list, and the {@code explanation}. The
 * {@code Set<Enum>} and {@code List<Enum>} fields flatten to comma-separated
 * enum-name strings because they are never-query-by-column child payloads
 * (data-model.md §7 — same precedent as {@code risk_factor.evidence_reference_ids}).
 * No domain logic. Owned by its parent {@code PolicyEntity} via cascade
 * (aggregate = policy + rules, data-model.md §7); the parent association is
 * the owning side of {@code policy_id} so Hibernate inserts it in the child
 * row. Mirrors {@code RiskFactorEntity}.
 *
 * <p><b>Note on the {@code data-model.md §E} sketch</b>: that sketch lists
 * {@code (condition, outcome)} columns, which is stale relative to the actual
 * {@code PolicyRule} record — rules are deterministic match sets, not
 * expression strings (policy-decision-domain.md §4: "no CEL/OPA"). This entity
 * persists the actual record shape: {@code ruleId}, the three match-set CSVs,
 * {@code outcome}, {@code actions} CSV, and {@code explanation}.
 */
@Entity
@Table(name = "policy_rule")
public class PolicyRuleEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private PolicyEntity policy;

  private String ruleId;

  private String targetTiers;

  private String targetRiskLevels;

  private String requiredEvidenceStates;

  private String outcome;

  private String actions;

  private String explanation;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public PolicyEntity getPolicy() { return policy; }

  public void setPolicy(PolicyEntity policy) { this.policy = policy; }

  public String getRuleId() { return ruleId; }

  public void setRuleId(String ruleId) { this.ruleId = ruleId; }

  public String getTargetTiers() { return targetTiers; }

  public void setTargetTiers(String targetTiers) { this.targetTiers = targetTiers; }

  public String getTargetRiskLevels() { return targetRiskLevels; }

  public void setTargetRiskLevels(String targetRiskLevels) { this.targetRiskLevels = targetRiskLevels; }

  public String getRequiredEvidenceStates() { return requiredEvidenceStates; }

  public void setRequiredEvidenceStates(String requiredEvidenceStates) {
    this.requiredEvidenceStates = requiredEvidenceStates;
  }

  public String getOutcome() { return outcome; }

  public void setOutcome(String outcome) { this.outcome = outcome; }

  public String getActions() { return actions; }

  public void setActions(String actions) { this.actions = actions; }

  public String getExplanation() { return explanation; }

  public void setExplanation(String explanation) { this.explanation = explanation; }
}
