package com.cdi.systemcontext.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code service} table.
 *
 * <p>Derived findBy queries backing the {@code ServiceRepository}
 * tenant-scoped lookups used by UC-09 duplicate detection
 * ({@code tenant_id, name}) and UC-15 reads ({@code tenant_id, id})
 * (application-layer.md §6, data-model.md §2/§5). The
 * {@code UNIQUE (tenant_id, name)} DB constraint (V8) is the race-safe final
 * guarantee.
 */
public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, UUID> {

  Optional<ServiceEntity> findByTenantIdAndName(UUID tenantId, String name);

  Optional<ServiceEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndName(UUID tenantId, String name);
}
