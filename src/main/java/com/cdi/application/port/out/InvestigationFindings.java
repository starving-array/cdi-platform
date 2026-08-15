package com.cdi.application.port.out;

import java.util.List;

/**
 * Structured output of an {@link AgentPort} investigation: an ordered list of
 * evidence-cited findings. Agent failures are surfaced as
 * {@link com.cdi.application.common.error.PortException} — the application
 * layer degrades to deterministic risk only.
 */
public record InvestigationFindings(List<InvestigationFinding> findings) {

  public InvestigationFindings {
    findings = findings == null ? List.of() : List.copyOf(findings);
  }
}