package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for the P2 SuspendOrganization capability
 * (application-layer.md §6). Synchronous, TENANT_ADMIN-only state change that
 * transitions the organization (tenant root) from {@code ACTIVE} to
 * {@code SUSPENDED} (organization-repository-service-domain.md,
 * {@code Organization.suspend()}).
 *
 * <p>The organization identifier is the tenant identity: the Organization
 * aggregate root <em>is</em> the tenant, so a single {@code tenantId} is the
 * complete scope and there is no separate {@code organizationId} field
 * (data-model.md §3.A). This command is <b>non-idempotent</b> and therefore
 * carries no {@code IdempotencyKey} — a repeated suspend of an already
 * suspended organization is rejected ({@code ORGANIZATION_ALREADY_SUSPENDED}),
 * never replayed (application-layer.md §10).
 */
public record SuspendOrganizationCommand(
    TenantId tenantId,
    Actor actor) {

  public SuspendOrganizationCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
  }
}
