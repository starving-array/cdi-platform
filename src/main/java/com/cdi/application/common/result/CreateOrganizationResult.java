package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

/**
 * Idempotent command result for UC-07 CreateOrganization: the organization
 * (tenant) produced or reused by the command, plus whether it was newly
 * created (application-layer.md §6).
 *
 * <p>The organization identifier is a {@link TenantId} because the
 * Organization aggregate root <em>is</em> the tenant: {@code Organization.id}
 * doubles as the tenant identity (organization-repository-service-domain.md,
 * data-model.md §3.A).
 */
public record CreateOrganizationResult(TenantId organizationId, boolean created)
    implements CommandResult {

  public CreateOrganizationResult {
    if (organizationId == null) {
      throw new DomainException("OrganizationId cannot be null");
    }
  }
}
