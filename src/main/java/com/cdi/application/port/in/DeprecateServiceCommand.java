package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for the P2 DeprecateService capability
 * (application-layer.md §6). Synchronous, TENANT_ADMIN-only state change that
 * transitions the service from {@code ACTIVE} to {@code DEPRECATED}
 * (organization-repository-service-domain.md, {@code Service.deprecate()}).
 *
 * <p>The service is a tenant-scoped child aggregate, so the command carries
 * both the scoping {@code tenantId} and the target {@code serviceId}
 * (data-model.md §2/§5). This command is <b>non-idempotent</b> and therefore
 * carries no {@code IdempotencyKey} — a repeated deprecation of an already
 * deprecated service is rejected ({@code SERVICE_ALREADY_DEPRECATED}),
 * never replayed (application-layer.md §10).
 */
public record DeprecateServiceCommand(
    TenantId tenantId,
    ServiceId serviceId,
    Actor actor) {

  public DeprecateServiceCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (serviceId == null) {
      throw new DomainException("ServiceId cannot be null");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
  }
}