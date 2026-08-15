package com.cdi.systemcontext.domain;

/**
 * Represents the business/operational criticality of a Service.
 */
public enum CriticalityTier {
    TIER_0, // Mission Critical / Core Infrastructure
    TIER_1, // Highly Important / Revenue Impacting
    TIER_2, // Internal Tooling / Medium Impact
    TIER_3  // Experimental / Low Impact
}
