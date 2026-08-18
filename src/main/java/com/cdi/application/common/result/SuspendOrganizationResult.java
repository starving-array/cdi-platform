package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;

/**
 * Command result for the P2 SuspendOrganization capability: the organization
 * (tenant) id and the resulting {@link Organization.Status} after the suspend
 * was persisted (application-layer.md §6).
 *
 * <p>The status returned is the post-transition state ({@code SUSPENDED});
 * the result carries the tenant identity because the Organization aggregate
 * root <em>is</em> the tenant (organization-repository-service-domain.md,
 * data-model.md §3.A).
 */
public record SuspendOrganizationResult(
    TenantId tenantId,
    Organization.Status status) implements CommandResult {

  public SuspendOrganizationResult {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (status == null) {
      throw new DomainException("Status cannot be null");
    }
  }
}
