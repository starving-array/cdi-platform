package com.cdi.policy.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.decision.domain.DecisionOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyRuleTest {

    @Test
    void shouldRejectInvalidPolicyRuleConstruction() {
        assertThrows(DomainException.class, () -> new PolicyRule(
                null, Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Reason"));

        assertThrows(DomainException.class, () -> new PolicyRule(
                "", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Reason"));

        assertThrows(DomainException.class, () -> new PolicyRule(
                "R1", Set.of(), Set.of(), Set.of(), null, List.of(), "Reason"));

        assertThrows(DomainException.class, () -> new PolicyRule(
                "R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), ""));
    }
}
