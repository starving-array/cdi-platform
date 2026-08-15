package com.cdi.evidence.domain;

/**
 * Minimal set of V1 source types.
 */
public enum SourceType {
    CHANGE,
    COMMIT,
    INCIDENT,
    POSTMORTEM,
    SERVICE,
    DEPENDENCY,
    DOCUMENTATION,
    TEST_HISTORY,
    DEPLOYMENT,
    OTHER
}
