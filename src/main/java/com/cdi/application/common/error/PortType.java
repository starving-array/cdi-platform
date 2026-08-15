package com.cdi.application.common.error;

/**
 * Identifies which outbound port produced a {@link PortException}, so the
 * orchestration layer can apply the documented per-port failure policy
 * (ports-and-adapters.md §4, application-layer.md §9.2).
 */
public enum PortType {
  CHANGE_REPOSITORY,
  ANALYSIS_RUN_REPOSITORY,
  RISK_ASSESSMENT_REPOSITORY,
  SOURCE_CONTROL,
  SYSTEM_CONTEXT,
  EVIDENCE_SEARCH,
  AGENT,
  JOB_QUEUE
}