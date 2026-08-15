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

    @Test
    void shouldRestorePersistedPolicy() {
        PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Valid");
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");

        Policy restored = Policy.restore(
                PolicyId.generate(),
                TenantId.generate(),
                "Restored Policy",
                "restored description",
                PolicyStatus.ARCHIVED,
                PolicyVersion.of("v2"),
                List.of(rule),
                createdAt);

        assertNotNull(restored.getId());
        assertEquals("Restored Policy", restored.getName());
        assertEquals("restored description", restored.getDescription());
        assertEquals(PolicyStatus.ARCHIVED, restored.getStatus());
        assertEquals("v2", restored.getVersion().value());
        assertEquals(1, restored.getRules().size());
        assertEquals(createdAt, restored.getCreatedAt());
    }

    @Test
    void shouldRestorePreserveActiveStatusAndVersion() {
        PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Valid");

        Policy restored = Policy.restore(
                PolicyId.generate(), TenantId.generate(), "Active Policy", "",
                PolicyStatus.ACTIVE, PolicyVersion.of("v3"), List.of(rule), Instant.now());

        assertEquals(PolicyStatus.ACTIVE, restored.getStatus());
        assertEquals("v3", restored.getVersion().value());
    }

    @Test
    void shouldRestoreRejectInvalidState() {
        PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Valid");
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> Policy.restore(
                null, TenantId.generate(), "Name", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), now));
        assertThrows(DomainException.class, () -> Policy.restore(
                PolicyId.generate(), TenantId.generate(), "", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), now));
        assertThrows(DomainException.class, () -> Policy.restore(
                PolicyId.generate(), TenantId.generate(), "Name", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(), now));
        assertThrows(NullPointerException.class, () -> Policy.restore(
                PolicyId.generate(), TenantId.generate(), "Name", "", PolicyStatus.ACTIVE, null, List.of(rule), now));
    }
}
