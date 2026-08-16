package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetPolicyQuery;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;

import java.util.Objects;

/**
 * Application query service UC-16 GetPolicy (application-layer.md §6/§4,
 * api-contract.md §2). Synchronous, read-only: returns the {@link Policy}
 * aggregate backing the identifier and serves as the P0 support query exposing
 * policy configuration (rules, version, status — application-layer.md §4 —
 * "no admin writes needed"). Never mutates state, never opens a write
 * transaction, never calls an external port, and is never idempotency-keyed
 * (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link GetPolicyQuery policyId}; the tenant is
 * resolved from the request context (data-model.md §2 — the policy id alone is
 * the worker/API payload). The active version is the policy's own current
 * version.
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): the lookup is scoped to
 * the context tenant via {@link PolicyRepository#findByTenantIdAndId} — a
 * policy that does not belong to the context tenant resolves to
 * {@link ApplicationError#POLICY_NOT_FOUND}; no cross-tenant read is possible
 * and no unscoped lookup is used.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-16's
 * actor is {@code ENGINEER} or {@code TENANT_ADMIN} (a supporting query for
 * both the engineer console and the workspace-owner UI).
 */
public final class GetPolicyQueryService {

  private final PolicyRepository policyRepository;

  public GetPolicyQueryService(PolicyRepository policyRepository) {
    this.policyRepository = Objects.requireNonNull(policyRepository, "PolicyRepository");
  }

  public Policy handle(GetPolicyQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    return policyRepository.findByTenantIdAndId(tenantId, query.policyId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.POLICY_NOT_FOUND));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}