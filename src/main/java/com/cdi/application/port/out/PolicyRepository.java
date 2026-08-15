package com.cdi.application.port.out;

import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyStatus;

import java.util.Optional;

/**
 * Outbound persistence port for the Policy aggregate, used by UC-05 policy
 * evaluation and UC-10 CreatePolicy (application-layer.md §6/§7,
 * policy-decision-domain.md §2).
 *
 * <p>Policies are strictly versioned and immutable history
 * (policy-decision-domain.md §3): evaluation must be traceable to the exact
 * {@code PolicyVersion} that was evaluated, so this port surfaces the active
 * {@code Policy} aggregate (with {@link PolicyStatus#ACTIVE}) and never a
 * mutable "current" snapshot.
 *
 * <p>The lookup is always tenant-scoped — a policy from tenant A is never
 * evaluated for tenant B (data-model.md §2/§5). No JPA, Spring Data, SQL, or
 * PostgreSQL types appear on this contract.
 *
 * <p><b>UC-10 contract (resolved audit Decision 1)</b>: exactly <em>one</em>
 * ACTIVE policy per tenant. {@link #findActiveByTenant(TenantId)} is therefore
 * the natural-key lookup that drives UC-10 idempotent reuse — the
 * {@code (tenantId)} natural key already identifies the single active policy
 * (the {@code name} field on {@code Policy} is descriptive, not a natural
 * key). {@link #save(Policy)} inserts a new active policy; the PostgreSQL
 * partial unique {@code UNIQUE (tenant_id) WHERE status = 'ACTIVE'} constraint
 * (V9) is the race-safe final guarantee that a tenant never holds more than one
 * active policy, surfacing a concurrent duplicate insert as a persistence-layer
 * {@code DataIntegrityViolationException} that propagates verbatim (never
 * converted into a fake successful creation — UC-10 contract, UC-08/UC-09
 * precedent). The pre-existing {@link #findActiveByTenant} read method is
 * unchanged; UC-10 adds only {@link #save}.
 */
public interface PolicyRepository {

  /**
   * Resolves the active policy for a tenant.
   *
   * @param tenantId the tenant whose active policy is requested
   * @return the active {@code Policy}, or empty if the tenant has no active
   *     policy configured
   */
  Optional<Policy> findActiveByTenant(TenantId tenantId);

  /**
   * Persists a new {@code Policy} aggregate (UC-10 CreatePolicy). The Policy
   * carries its own {@code TenantId} (it is a tenant-scoped child aggregate),
   * so no separate {@code tenantId} argument is required. The caller (the
   * application handler) is responsible for the idempotent-reuse check via
   * {@link #findActiveByTenant}; the underlying
   * {@code UNIQUE (tenant_id) WHERE status = 'ACTIVE'} constraint (V9) is the
   * DB backstop for concurrent races.
   *
   * @param policy the policy to persist
   * @return the persisted policy, rebuilt via {@code Policy.restore}
   */
  Policy save(Policy policy);
}
