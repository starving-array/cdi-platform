package com.cdi.decision.domain;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanOverrideTest {

    @Test
    void shouldRejectInvalidHumanOverride() {
        assertThrows(DomainException.class, () -> new HumanOverride(
                null, DecisionOutcome.BLOCK, DecisionOutcome.APPROVE, "Reason", Instant.now()));

        assertThrows(DomainException.class, () -> new HumanOverride(
                "actor-123", null, DecisionOutcome.APPROVE, "Reason", Instant.now()));

        assertThrows(DomainException.class, () -> new HumanOverride(
                "actor-123", DecisionOutcome.BLOCK, DecisionOutcome.APPROVE, "", Instant.now()));
                
        assertThrows(DomainException.class, () -> new HumanOverride(
                "actor-123", DecisionOutcome.BLOCK, DecisionOutcome.APPROVE, "Reason", null));
    }
}
