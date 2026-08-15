package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyVersion;

import java.util.List;

/**
 * Input contract for UC-10 CreatePolicy (application-layer.md §6).
 * Immutable; the handler checks for an existing <em>active</em> policy for the
 * tenant and, on a duplicate, returns the existing policy with
 * {@code created=false}.
 *
 * <p><b>Natural key (resolved audit Decision 1)</b>: exactly one ACTIVE policy
 * per tenant — the natural key is {@code (tenantId)}. The {@code name} field is
 * descriptive metadata on the policy, not a natural key (contrast UC-09 where
 * the service {@code name} scoped by {@code tenantId} is the natural key). The
 * {@code IdempotencyKey} is carried for transport-level correlation; the
 * semantic duplicate check is the existing {@code findActiveByTenant} lookup.
 *
 * <p>The {@code version} is the initial {@link PolicyVersion} label for a new
 * policy (e.g. {@code "v1"}); the {@code rules} list must contain at least one
 * {@link PolicyRule} — the {@code Policy} aggregate invariant
 * (policy-decision-domain.md §2). The {@code description} may be blank/null
 * (the {@code Policy} constructor coerces null to {@code ""}).
 */
public record CreatePolicyCommand(
    TenantId tenantId,
    String name,
    String description,
    PolicyVersion version,
    List<PolicyRule> rules,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public CreatePolicyCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (name == null || name.isBlank()) {
      throw new DomainException("Policy name cannot be blank");
    }
    if (version == null) {
      throw new DomainException("PolicyVersion cannot be null");
    }
    if (rules == null || rules.isEmpty()) {
      throw new DomainException("Policy must contain at least one rule");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    name = name.trim();
    if (description != null) {
      description = description.trim();
    }
    rules = List.copyOf(rules);
  }
}
