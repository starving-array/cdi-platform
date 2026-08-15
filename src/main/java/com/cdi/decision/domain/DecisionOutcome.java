package com.cdi.decision.domain;

/**
 * Deterministic outcomes of a policy evaluation.
 */
public enum DecisionOutcome {
    /**
     * Policy permits the change without additional human review.
     */
    APPROVE,
    
    /**
     * Change may proceed only after designated human review/approval.
     */
    REVIEW_REQUIRED,
    
    /**
     * Change must not proceed under the applicable policy.
     */
    BLOCK
}
