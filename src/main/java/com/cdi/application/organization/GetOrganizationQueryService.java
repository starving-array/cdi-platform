package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetOrganizationQuery;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;

import java.util.Objects;

/**
 * Application query service UC-13 GetOrganization (application-layer.md §6/§4,
 * api-contract.md §4). Synchronous, read-only: returns the
 * {@link Organization} aggregate backing the context tenant and supports the
 * P0 slice's cheap, direct reads (application-layer.md §4 — "no admin writes
 * needed"). Never mutates state, never opens a write transaction, never calls
 * an external port, and is never idempotency-keyed (queries are not keyed —
 * application-layer.md §6).
 *
 * <p><b>Lookup</b>: by {@link GetOrganizationQuery tenantId}. Because the
 * Organization <em>is</em> the tenant root, its {@code id} already is the
 * tenant identity (data-model.md §3.A) and the lookup key is resolved
 * {@linkplain OrganizationRepository#findById directly} from the request
 * payload.
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): the query-target tenant
 * must equal the tenant resolved from the request context; any mismatch
 * resolves to {@link ApplicationError#ORGANIZATION_NOT_FOUND} — a context
 * tenant can never observe another tenant's organization.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-13's
 * actor is {@code ENGINEER} or {@code TENANT_ADMIN} (a supporting query for
 * both the engineer console and the workspace-owner UI).
 */
public final class GetOrganizationQueryService {

  private final OrganizationRepository organizationRepository;

  public GetOrganizationQueryService(OrganizationRepository organizationRepository) {
    this.organizationRepository = Objects.requireNonNull(organizationRepository, "OrganizationRepository");
  }

  public Organization handle(GetOrganizationQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    if (!query.tenantId().equals(tenantId)) {
      throw new ApplicationException(ApplicationError.ORGANIZATION_NOT_FOUND);
    }
    return organizationRepository.findById(tenantId)
        .orElseThrow(() -> new ApplicationException(ApplicationError.ORGANIZATION_NOT_FOUND));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}