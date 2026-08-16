package com.cdi.policy.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code policy} table.
 *
 * <p>Derived findBy query backing the {@code PolicyRepository}
 * tenant-scoped active-policy lookup used by UC-05 policy evaluation and UC-10
 * idempotent reuse (application-layer.md §6, data-model.md §E/§5). The
 * {@code UNIQUE (tenant_id) WHERE status = 'ACTIVE'} partial unique constraint
 * (V9) is the race-safe final guarantee that at most one active policy exists
 * per tenant.
 */
public interface PolicyJpaRepository extends JpaRepository<PolicyEntity, UUID> {

  Optional<PolicyEntity> findByTenantIdAndStatus(UUID tenantId, String status);

  Optional<PolicyEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndStatus(UUID tenantId, String status);
}
