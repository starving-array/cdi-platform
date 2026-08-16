package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListPolicyVersionsQuery;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;

import java.util.List;
import java.util.Objects;

/**
 * Application query service UC-18 ListPolicyVersions (application-layer.md §6,
 * api-contract.md §2.3). Synchronous, read-only: lists every policy version
 * for the tenant-scoped policy identified by {@code policyId} — both the
 * ACTIVE version and ARCHIVED historical versions (policy-decision-domain.md
 * §3: historical versions are retained for traceability). Never mutates state,
 * never opens a write transaction, never calls an external port, and is never
 * idempotency-keyed (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link ListPolicyVersionsQuery policyId}; the
 * tenant is resolved from the request context. The policy is resolved first
 * through the tenant-scoped {@link PolicyRepository#findByTenantIdAndId} so a
 * missing or cross-tenant policy resolves to
 * {@link ApplicationError#POLICY_NOT_FOUND} — never an empty list — then the
 * tenant's full version history is listed via
 * {@link PolicyRepository#findAllByTenantId}, preserving deterministic
 * oldest→newest ({@code createdAt} asc, {@code id} tie-breaker) ordering and
 * the full aggregate (version label, status, rules) of every version.
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): both reads are
 * tenant-scoped; a policy that does not belong to the effective tenant
 * resolves to {@code POLICY_NOT_FOUND}; a valid policy always has at least the
 * resolved version itself, so the list is never empty.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-18's
 * actor is {@code TENANT_ADMIN} (the versioning/workspace-owner UI).
 */
public final class ListPolicyVersionsQueryService {

  private final PolicyRepository policyRepository;

  public ListPolicyVersionsQueryService(PolicyRepository policyRepository) {
    this.policyRepository = Objects.requireNonNull(policyRepository, "PolicyRepository");
  }

  public List<Policy> handle(ListPolicyVersionsQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    policyRepository.findByTenantIdAndId(tenantId, query.policyId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.POLICY_NOT_FOUND));
    return policyRepository.findAllByTenantId(tenantId);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}