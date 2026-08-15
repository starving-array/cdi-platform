package com.cdi.application.port.out;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.EvidenceId;

import java.util.List;

/**
 * Structured, strongly typed finding produced by the investigation agent.
 * Citations reference real {@link EvidenceId}s; the application layer
 * structurally validates them before persisting (ports-and-adapters.md §2.4).
 */
public record InvestigationFinding(String summary, String explanation, List<EvidenceId> evidenceCitations) {

  public InvestigationFinding {
    if (summary == null || summary.isBlank()) {
      throw new DomainException("Finding summary cannot be blank");
    }
    explanation = explanation != null ? explanation.trim() : "";
    evidenceCitations = evidenceCitations == null ? List.of() : List.copyOf(evidenceCitations);
    summary = summary.trim();
  }
}