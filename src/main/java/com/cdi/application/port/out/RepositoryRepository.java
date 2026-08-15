package com.cdi.application.port.out;

import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;

import java.util.Optional;

/**
 * Outbound persistence port for the Repository aggregate, used by UC-08
 * CreateRepository (application-layer.md §6). Adapters persist to the
 * {@code repository} table (data-model.md §3, V7 migration), keyed by the
 * repository's own id.
 *
 * <p>Only the operations genuinely required by UC-08 are declared: the
 * tenant-scoped natural-key lookup that drives duplicate detection and the
 * insert. No JPA, Spring Data, SQL, or PostgreSQL types appear on this
 * contract.
 *
 * <p><b>Tenant-isolation note</b>: the Repository is a tenant-scoped child
 * aggregate — {@code tenantId} is the explicit scoping parameter of
 * {@link #findByTenantIdProviderTypeExternalId}. There is intentionally no
 * global (tenant-less) lookup here; the organization/tenant-root exception
 * applied by {@code OrganizationRepository.findByName} does <em>not</em> apply
 * to repositories (organization-repository-service-domain.md, data-model.md
 * §2). A global name/external-id lookup would be a cross-tenant leak.
 */
public interface RepositoryRepository {

  Optional<Repository> findByTenantIdProviderTypeExternalId(
      TenantId tenantId, Repository.ProviderType providerType, String externalId);

  Repository save(Repository repository);
}
