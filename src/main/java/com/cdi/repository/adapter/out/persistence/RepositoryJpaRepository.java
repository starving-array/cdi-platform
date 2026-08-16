package com.cdi.repository.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code repository} table.
 *
 * <p>Derived findBy query backing the {@code RepositoryRepository}
 * tenant-scoped natural-key lookup ({@code tenant_id, provider_type,
 * external_id}) used by UC-08 duplicate detection (application-layer.md §6,
 * data-model.md §5). The {@code UNIQUE (tenant_id, provider_type,
 * external_id)} DB constraint (V7) is the race-safe final guarantee.
 */
public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, UUID> {

  Optional<RepositoryEntity> findByTenantIdAndProviderTypeAndExternalId(
      UUID tenantId, String providerType, String externalId);

  Optional<RepositoryEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndProviderTypeAndExternalId(
      UUID tenantId, String providerType, String externalId);
}
