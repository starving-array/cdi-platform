package com.cdi.policy.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Policy Aggregate Root.
 */
public class Policy {
    private final PolicyId id;
    private final TenantId tenantId;
    private final String name;
    private final String description;
    
    private final PolicyStatus status;
    private final PolicyVersion version;
    private final List<PolicyRule> rules;
    
    private final Instant createdAt;

    public Policy(PolicyId id, TenantId tenantId, String name, String description, 
                  PolicyStatus status, PolicyVersion version, List<PolicyRule> rules, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "PolicyId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        
        if (name == null || name.isBlank()) throw new DomainException("Policy name cannot be blank");
        this.name = name;
        this.description = description == null ? "" : description;
        
        this.status = Objects.requireNonNull(status, "PolicyStatus cannot be null");
        this.version = Objects.requireNonNull(version, "PolicyVersion cannot be null");
        
        if (rules == null || rules.isEmpty()) {
            throw new DomainException("Policy must contain at least one rule");
        }
        this.rules = List.copyOf(rules);
        
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt timestamp cannot be null");
    }

    /**
     * Reconstructs a Policy from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status and version are
     * supplied explicitly rather than defaulted by the constructor. This is a
     * persistence-read helper; it performs no state transitions and does not
     * alter existing constructor semantics. Mirrors {@code Organization#restore}
     * and {@code Service#restore} (UC-07/UC-09 precedent).
     */
    public static Policy restore(PolicyId id, TenantId tenantId, String name, String description,
                                 PolicyStatus status, PolicyVersion version,
                                 List<PolicyRule> rules, Instant createdAt) {
        Policy policy = new Policy(id, tenantId, name, description, status, version, rules, createdAt);
        return policy;
    }

    public PolicyId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PolicyStatus getStatus() { return status; }
    public PolicyVersion getVersion() { return version; }
    public List<PolicyRule> getRules() { return rules; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Policy policy = (Policy) o;
        return id.equals(policy.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
