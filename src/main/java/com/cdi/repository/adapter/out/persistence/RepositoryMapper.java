package com.cdi.repository.adapter.out.persistence;

import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;

/**
 * Maps between the {@code Repository} aggregate and its persistence
 * representation.
 *
 * <p>The {@code Repository} aggregate carries its own {@code TenantId}
 * directly (it is a tenant-scoped child aggregate), so the conversion needs no
 * separate {@code tenantId} argument — the tenant scope is read/written from
 * the aggregate itself (contrast {@code ChangeMapper}, which receives
 * {@code TenantId} on the port call because {@code Change} does not store it).
 *
 * <p>The {@code updated_at} column is initialized to {@code createdAt} on
 * write; the domain Repository exposes {@code updatedAt} (it equals
 * {@code createdAt} for newly created aggregate). On read the entity's
 * {@code updatedAt} is restored verbatim via {@link Repository#restore}.
 */
final class RepositoryMapper {

  private RepositoryMapper() {
    // utility class
  }

  static RepositoryEntity toEntity(Repository repository) {
    RepositoryEntity entity = new RepositoryEntity();
    entity.setId(repository.getId().value());
    entity.setTenantId(repository.getTenantId().value());
    entity.setProviderType(repository.getProviderType().name());
    entity.setExternalId(repository.getExternalId());
    entity.setName(repository.getName());
    entity.setUrl(repository.getUrl());
    entity.setDefaultBranch(repository.getDefaultBranch());
    entity.setStatus(repository.getStatus().name());
    entity.setCreatedAt(repository.getCreatedAt());
    entity.setUpdatedAt(repository.getUpdatedAt());
    return entity;
  }

  static Repository toDomain(RepositoryEntity entity) {
    return Repository.restore(
        new RepositoryId(entity.getId()),
        new TenantId(entity.getTenantId()),
        Repository.ProviderType.valueOf(entity.getProviderType()),
        entity.getExternalId(),
        entity.getName(),
        entity.getUrl(),
        entity.getDefaultBranch(),
        Repository.Status.valueOf(entity.getStatus()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
