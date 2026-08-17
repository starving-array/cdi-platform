package com.cdi.decision.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code human_override} table (data-model.md §E,
 * V11, ADR-006).
 *
 * <p>1:1 child of {@code DecisionRecordEntity} recording the single,
 * immutable human override of a generated decision (UC-06 OverrideDecision).
 * Persistence-only mapping of the {@code HumanOverride} value object: the
 * travel-scoped outcomes plus the actor identity and the required
 * justification (policy-decision-domain.md §10). The original
 * {@code decision_record} row is never modified by an override — this row is
 * supplemental to it.
 */
@Entity
@Table(name = "human_override")
public class HumanOverrideEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decision_record_id")
  private DecisionRecordEntity decisionRecord;

  private String originalOutcome;

  private String newOutcome;

  private String actorId;

  private String justification;

  private Instant overrideAt;

  private Instant createdAt;

  private Instant updatedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public DecisionRecordEntity getDecisionRecord() { return decisionRecord; }

  public void setDecisionRecord(DecisionRecordEntity decisionRecord) { this.decisionRecord = decisionRecord; }

  public String getOriginalOutcome() { return originalOutcome; }

  public void setOriginalOutcome(String originalOutcome) { this.originalOutcome = originalOutcome; }

  public String getNewOutcome() { return newOutcome; }

  public void setNewOutcome(String newOutcome) { this.newOutcome = newOutcome; }

  public String getActorId() { return actorId; }

  public void setActorId(String actorId) { this.actorId = actorId; }

  public String getJustification() { return justification; }

  public void setJustification(String justification) { this.justification = justification; }

  public Instant getOverrideAt() { return overrideAt; }

  public void setOverrideAt(Instant overrideAt) { this.overrideAt = overrideAt; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}