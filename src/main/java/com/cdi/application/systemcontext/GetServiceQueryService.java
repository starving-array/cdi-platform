package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetServiceQuery;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.Service;

import java.util.Objects;

/**
 * Application query service UC-15 GetService (application-layer.md §6/§4,
 * api-contract.md §2.2). Synchronous, read-only: returns the {@link Service}
 * aggregate backing the identifier and serves as the P0 support query exposing
 * system-context metadata (criticality, owner, status — application-layer.md
 * §4 — "no admin writes needed"). Never mutates state, never opens a write
 * transaction, never calls an external port, and is never idempotency-keyed
 * (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link GetServiceQuery serviceId}; the tenant
 * is resolved from the request context (data-model.md §2 — the service id
 * alone is the worker/API payload).
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): the lookup is scoped to
 * the context tenant via
 * {@link ServiceRepository#findByTenantIdAndId} — a service that does not
 * belong to the context tenant resolves to
 * {@link ApplicationError#SERVICE_NOT_FOUND}; no cross-tenant read is possible
 * and no unscoped lookup is used.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-15's
 * actor is {@code ENGINEER} or {@code TENANT_ADMIN} (a supporting query for
 * both the engineer console and the workspace-owner UI).
 */
public final class GetServiceQueryService {

  private final ServiceRepository serviceRepository;

  public GetServiceQueryService(ServiceRepository serviceRepository) {
    this.serviceRepository = Objects.requireNonNull(serviceRepository, "ServiceRepository");
  }

  public Service handle(GetServiceQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    return serviceRepository.findByTenantIdAndId(tenantId, query.serviceId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.SERVICE_NOT_FOUND));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}