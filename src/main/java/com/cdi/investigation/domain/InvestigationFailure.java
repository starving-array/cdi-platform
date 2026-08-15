package com.cdi.investigation.domain;

import com.cdi.common.domain.exception.DomainException;

import java.time.Instant;

/**
 * Value object describing why an {@code AgentInvestigation} failed
 * (analysis-workflow.md §7).
 *
 * <p>Agent failures are explicitly represented — never hidden — and remain
 * distinguishable from deterministic risk-evaluation failures.
 */
public record InvestigationFailure(FailureCategory category, String failureCode, Instant failedAt) {

  public enum FailureCategory {
    AGENT_UNAVAILABLE,
    INVESTIGATION_FAILED
  }

  public InvestigationFailure {
    if (category == null) {
      throw new DomainException("Failure category cannot be null");
    }
    if (failedAt == null) {
      throw new DomainException("Failure timestamp cannot be null");
    }
    failureCode = failureCode != null ? failureCode.trim() : "";
  }
}
