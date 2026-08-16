package com.cdi.application.port.out;

import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.Service;

import java.util.Optional;

/**
 * Outbound persistence port for the Service aggregate, used by UC-09
 * CreateService and UC-15 GetService (application-layer.md §6). Adapters
 * persist to the {@code service} table (data-model.md §3.A, V8 migration),
 * keyed by the service's own id.
 *
 * <p>Only the operations genuinely required by those use cases are declared:
 * the tenant-scoped natural-key lookup that drives duplicate detection, the
 * tenant-scoped by-id lookup that serves UC-15 reads, and the insert. No JPA,
 * Spring Data, SQL, or PostgreSQL types appear on this contract.
 *
 * <p><b>Tenant-isolation note</b>: the Service is a tenant-scoped child
 * aggregate — {@code tenantId} is the explicit scoping parameter of
 * {@link #findByTenantIdAndName} and {@link #findByTenantIdAndId}. There is
 * intentionally no global (tenant-less) lookup here (contrast the
 * organization/tenant-root {@code OrganizationRepository.findByName} which is
 * the documented multi-tenant special case). A global name/external-id lookup
 * would be a cross-tenant leak.
 */
public interface ServiceRepository {

  Optional<Service> findByTenantIdAndName(TenantId tenantId, String name);

  Optional<Service> findByTenantIdAndId(TenantId tenantId, ServiceId serviceId);

  Service save(Service service);
}
