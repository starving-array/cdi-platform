package com.cdi.systemcontext.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code service} table.
 *
 * <p>Derived findBy query backing the {@code ServiceRepository}
 * tenant-scoped natural-key lookup ({@code tenant_id, name}) used by UC-09
 * duplicate detection (application-layer.md §6, data-model.md §5). The
 * {@code UNIQUE (tenant_id, name)} DB constraint (V8) is the race-safe final
 * guarantee.
 */
public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, UUID> {

  Optional<ServiceEntity> findByTenantIdAndName(UUID tenantId, String name);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndName(UUID tenantId, String name);
}
