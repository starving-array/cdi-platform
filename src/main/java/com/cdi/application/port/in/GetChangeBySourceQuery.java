package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

/**
 * Source-reference input contract for UC-11 GetChange (application-layer.md
 * §6): look up a change by its SCM source reference
 * {@code (tenantId, repositoryId, providerChangeId)} instead of the primary
 * {@link GetChangeQuery#changeId()}. The source-reference form is added
 * together with the {@code GetChangeQueryService} implementation
 * (application-foundation.md §6).
 *
 * <p>The tenant is carried on this form because the source reference is
 * inherently tenant-scoped (data-model.md §2/§5); the primary form resolves
 * the tenant from the request context instead.
 */
public record GetChangeBySourceQuery(
    TenantId tenantId,
    RepositoryId repositoryId,
    String providerChangeId) {

  public GetChangeBySourceQuery {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
    if (providerChangeId == null || providerChangeId.isBlank()) {
      throw new DomainException("Provider change ID cannot be blank");
    }
    providerChangeId = providerChangeId.trim();
  }
}
