package com.cdi.application.port.out;

import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyStatus;

import java.util.List;
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
 * unchanged; UC-10 adds only {@link #save}. UC-16 GetPolicy adds the
 * tenant-scoped primary lookup {@link #findByTenantIdAndId} (additive, matches
 * the UC-13/UC-14/UC-15 query precedent).
 */
public interface PolicyRepository {

  /**
   * Resolves the policy for a tenant by primary identifier (UC-16 GetPolicy,
   * application-layer.md §6). The lookup is always tenant-scoped — a policy
   * from tenant A is never visible to tenant B (data-model.md §2/§5); a policy
   * that does not belong to the context tenant resolves to empty, never a
   * cross-tenant read. The query services use only this scoped lookup and never
   * an unscoped id lookup.
   *
   * @param tenantId the context tenant
   * @param policyId the policy identifier
   * @return the matching {@code Policy} aggregate (parent + rules), or empty if
   *     the tenant has no such policy
   */
  Optional<Policy> findByTenantIdAndId(TenantId tenantId, PolicyId policyId);

  /**
   * Lists every policy version for a tenant — the UC-18 ListPolicyVersions
   * history (application-layer.md §6 UC-18, policy-decision-domain.md §3).
   * Returns all statuses (the ACTIVE version plus ARCHIVED historical
   * versions, which coexist per tenant — V9), each as a full {@code Policy}
   * aggregate. Ordered by {@code createdAt} ascending (oldest first) with
   * {@code id} as the deterministic tie-breaker. The lookup is always
   * tenant-scoped — cross-tenant rows are never returned.
   *
   * @param tenantId the context tenant
   * @return the tenant's policy versions, oldest first; empty if the tenant
   *     has no policy rows
   */
  List<Policy> findAllByTenantId(TenantId tenantId);

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
