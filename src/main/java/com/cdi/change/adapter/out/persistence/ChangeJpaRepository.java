package com.cdi.change.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code change} table.
 *
 * <p>Derived findBy query backing the {@code ChangeRepository} natural-key
 * lookup ({@code tenant_id, repository_id, provider_change_id}) used by UC-01
 * duplicate detection (application-layer.md §6, data-model.md §5).
 */
public interface ChangeJpaRepository extends JpaRepository<ChangeEntity, UUID> {

  Optional<ChangeEntity> findByTenantIdAndRepositoryIdAndProviderChangeId(
      UUID tenantId, UUID repositoryId, String providerChangeId);

  Optional<ChangeEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  java.util.List<ChangeEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId);

  java.util.List<ChangeEntity> findByTenantIdAndRepositoryIdOrderByCreatedAtDescIdDesc(
      UUID tenantId, UUID repositoryId);

  long countByTenantId(UUID tenantId);
}