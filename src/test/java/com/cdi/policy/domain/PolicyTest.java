package com.cdi.policy.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {

    @Test
    void shouldCreateValidPolicy() {
        PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Valid");
        Policy policy = new Policy(
                PolicyId.generate(),
                TenantId.generate(),
                "Core Policy",
                "Description",
                PolicyStatus.ACTIVE,
                PolicyVersion.of("v1"),
                List.of(rule),
                Instant.now()
        );

        assertNotNull(policy.getId());
        assertEquals("Core Policy", policy.getName());
        assertEquals("Description", policy.getDescription());
        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());
        assertEquals("v1", policy.getVersion().value());
        assertEquals(1, policy.getRules().size());
        assertNotNull(policy.getCreatedAt());
    }

    @Test
    void shouldRejectInvalidPolicyConstruction() {
        PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Valid");
        
        assertThrows(NullPointerException.class, () -> new Policy(
                null, TenantId.generate(), "Name", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), Instant.now()));

        assertThrows(NullPointerException.class, () -> new Policy(
                PolicyId.generate(), null, "Name", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), Instant.now()));

        assertThrows(DomainException.class, () -> new Policy(
                PolicyId.generate(), TenantId.generate(), "", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), Instant.now()));

        assertThrows(DomainException.class, () -> new Policy(
                PolicyId.generate(), TenantId.generate(), "Name", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(), Instant.now()));
    }
}
