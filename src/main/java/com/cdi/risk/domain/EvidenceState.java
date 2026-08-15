package com.cdi.risk.domain;

/**
 * Indicates the availability of evidence during risk assessment.
 * This determines assessment completeness but does not blindly alter risk.
 */
public enum EvidenceState {
    EVIDENCE_AVAILABLE,
    NO_RELEVANT_EVIDENCE,
    EVIDENCE_RETRIEVAL_FAILED
}
