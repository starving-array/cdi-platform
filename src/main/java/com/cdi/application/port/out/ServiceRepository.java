package com.cdi.application.port.out;

import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.Service;

import java.util.Optional;

/**
 * Outbound persistence port for the Service aggregate, used by UC-09
 * CreateService (application-layer.md §6). Adapters persist to the
 * {@code service} table (data-model.md §3.A, V8 migration), keyed by the
 * service's own id.
 *
 * <p>Only the operations genuinely required by UC-09 are declared: the
 * tenant-scoped natural-key lookup that drives duplicate detection and the
 * insert. No JPA, Spring Data, SQL, or PostgreSQL types appear on this
 * contract.
 *
 * <p><b>Tenant-isolation note</b>: the Service is a tenant-scoped child
 * aggregate — {@code tenantId} is the explicit scoping parameter of
 * {@link #findByTenantIdAndName}. There is intentionally no global
 * (tenant-less) lookup here (contrast the organization/tenant-root
 * {@code OrganizationRepository.findByName} which is the documented
 * multi-tenant special case). A global name/external-id lookup would be a
 * cross-tenant leak.
 */
public interface ServiceRepository {

  Optional<Service> findByTenantIdAndName(TenantId tenantId, String name);

  Service save(Service service);
}
