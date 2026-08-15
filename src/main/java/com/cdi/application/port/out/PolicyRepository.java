package com.cdi.application.port.out;

import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyStatus;

import java.util.Optional;

/**
 * Read-only outbound port resolving the tenant's policy for UC-05 policy
 * evaluation (application-layer.md §6/§7, policy-decision-domain.md §2).
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
}
