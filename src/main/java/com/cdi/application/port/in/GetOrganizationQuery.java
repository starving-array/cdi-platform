package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for UC-13 GetOrganization (application-layer.md §6).
 */
public record GetOrganizationQuery(TenantId tenantId) {

  public GetOrganizationQuery {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
  }
}