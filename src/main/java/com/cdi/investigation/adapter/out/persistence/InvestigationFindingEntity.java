package com.cdi.investigation.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA representation of the {@code investigation_finding} table (data-model.md
 * §F/§D).
 *
 * <p>Persistence-only mapping of the {@code InvestigationFinding} value object:
 * summary, explanation, and ground-truth evidence citations. The cited
 * {@code EvidenceId}s are flattened to a comma-separated UUID string because
 * they are a never-query-by-column child payload (mirroring
 * {@code risk_factor.evidence_reference_ids}, data-model.md §7). Owned by its
 * parent {@code AgentInvestigationEntity} via a cascade; the parent
 * association is the owning side of {@code agent_investigation_id} so Hibernate
 * inserts it in the child row.
 */
@Entity
@Table(name = "investigation_finding")
public class InvestigationFindingEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "agent_investigation_id", nullable = false)
  private AgentInvestigationEntity agentInvestigation;

  private String summary;

  private String explanation;

  private String evidenceCitationIds;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public AgentInvestigationEntity getAgentInvestigation() { return agentInvestigation; }
  public void setAgentInvestigation(AgentInvestigationEntity agentInvestigation) {
    this.agentInvestigation = agentInvestigation;
  }

  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }

  public String getExplanation() { return explanation; }
  public void setExplanation(String explanation) { this.explanation = explanation; }

  public String getEvidenceCitationIds() { return evidenceCitationIds; }
  public void setEvidenceCitationIds(String evidenceCitationIds) { this.evidenceCitationIds = evidenceCitationIds; }
}
