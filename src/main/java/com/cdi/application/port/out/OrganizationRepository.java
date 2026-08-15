package com.cdi.application.port.out;

import com.cdi.organization.domain.Organization;

import java.util.Optional;

/**
 * Outbound persistence port for the Organization (tenant) aggregate, used by
 * UC-07 CreateOrganization (application-layer.md §6). Adapters persist to the
 * {@code tenant} table (data-model.md §3.A), keyed by the organization's own
 * {@code id} which doubles as the tenant identity.
 *
 * <p>Only the operations genuinely required by UC-07 are declared: the
 * natural-key (name) lookup that drives duplicate detection and the insert.
 * No JPA, Spring Data, SQL, or PostgreSQL types appear on this contract.
 *
 * <p><b>Tenant-isolation note</b>: the {@code findByName} lookup is global
 * (not tenant-scoped) because the Organization <em>is</em> the tenant root
 * — the organization's {@code id} is the tenant identity
 * (organization-repository-service-domain.md, data-model.md §3.A). A
 * name-based lookup is therefore not a cross-tenant leak; it identifies the
 * single tenant that owns that name.
 */
public interface OrganizationRepository {

  Optional<Organization> findByName(String name);

  Organization save(Organization organization);
}
