package com.cdi.evidence.domain;

/**
 * Indicates how the evidence was obtained.
 */
public enum EvidenceOrigin {
    /** Evidence derived directly from known system facts (e.g., changed file count). */
    DETERMINISTIC,
    
    /** Evidence retrieved from historical/system sources (e.g., previous PR). */
    RETRIEVED,
    
    /** Evidence identified by the investigation agent. */
    AGENT_DISCOVERED
}
