package com.cdi.application.common.error;

/**
 * Canonical application-layer error catalog (application-layer.md §9.1,
 * use-cases.md §3).
 *
 * <p>The application layer raises exactly one {@link ApplicationException}
 * carrying one of these errors. Each error exposes a stable code, a
 * human-readable default message, and a retryable/non-retryable
 * classification. HTTP mapping is deliberately <em>not</em> part of this
 * contract — the API layer performs that translation.
 */
public enum ApplicationError {

  CHANGE_NOT_FOUND("No change exists for the given identifiers", false),
  ANALYSIS_RUN_NOT_FOUND("No analysis run exists for the given id", false),
  ORGANIZATION_NOT_FOUND("No organization exists for the given tenant", false),
  REPOSITORY_NOT_FOUND("No repository exists for the given tenant", false),
  SERVICE_NOT_FOUND("No service exists for the given tenant", false),
  POLICY_NOT_FOUND("No policy exists for the given tenant", false),
  ANALYSIS_ALREADY_RUNNING("An analysis for this change is already running", false),
  ANALYSIS_SUPERSEDED("A newer commit has invalidated this analysis", false),
  INSUFFICIENT_CONTEXT("Required architecture metadata is missing", false),
  EVIDENCE_COLLECTION_FAILED("Evidence collection failed", false),
  AGENT_INVESTIGATION_FAILED("AI investigation failed", true),
  POLICY_EVALUATION_FAILED("Policy evaluation failed", false),
  UNAUTHORIZED("Actor lacks the required role", false);

  private final String defaultMessage;
  private final boolean retryable;

  ApplicationError(String defaultMessage, boolean retryable) {
    this.defaultMessage = defaultMessage;
    this.retryable = retryable;
  }

  /** Stable application error code (the enum name). */
  public String code() {
    return name();
  }

  /** Human-readable default message for this error category. */
  public String defaultMessage() {
    return defaultMessage;
  }

  /** Whether a retry attempt is permitted by the documented failure policy. */
  public boolean retryable() {
    return retryable;
  }
}