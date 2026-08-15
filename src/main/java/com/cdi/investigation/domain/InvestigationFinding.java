package com.cdi.investigation.domain;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Structured qualitative finding produced by an investigation
 * (domain-model.md §F, use-cases.md §5.3).
 *
 * <p>Unlike the {@code RiskAssessment}, a finding is qualitative — it
 * supplements rather than replaces the deterministic risk score. Each finding
 * carries {@link EvidenceCitation}s grounding it to real evidence identifiers;
 * a finding with none is a purely qualitative observation and is never
 * presented as evidence-backed certainty.
 */
public record InvestigationFinding(
    String summary, String explanation, List<EvidenceCitation> citations) {

  public InvestigationFinding {
    if (summary == null || summary.isBlank()) {
      throw new DomainException("Finding summary cannot be blank");
    }
    summary = summary.trim();
    explanation = explanation != null ? explanation.trim() : "";
    citations = citations == null ? List.of() : List.copyOf(citations);
  }

  /**
   * All evidence identifiers cited by this finding.
   */
  public List<com.cdi.common.domain.id.EvidenceId> citedEvidenceIds() {
    return citations.stream().map(EvidenceCitation::evidenceId).toList();
  }
}
