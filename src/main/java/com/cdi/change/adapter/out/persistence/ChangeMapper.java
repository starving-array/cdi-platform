package com.cdi.change.adapter.out.persistence;

import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

/**
 * Maps between the {@code Change} aggregate and its persistence representation.
 *
 * <p>Horizontal conversion only: the adapter stores the tenant scope alongside
 * the aggregate row because {@code TenantId} lives on the port call, not on the
 * aggregate itself (application-layer.md §7 / data-model.md §2).
 */
final class ChangeMapper {

  private ChangeMapper() {
    // utility class
  }

  static ChangeEntity toEntity(TenantId tenantId, Change change) {
    ChangeEntity entity = new ChangeEntity();
    entity.setId(change.getId().value());
    entity.setTenantId(tenantId.value());
    entity.setRepositoryId(change.getRepositoryId().value());
    entity.setProviderChangeId(change.getProviderChangeId());
    entity.setTitle(change.getTitle());
    entity.setDescription(change.getDescription());
    entity.setAuthor(change.getAuthor());
    entity.setSourceBranch(change.getSourceBranch());
    entity.setTargetBranch(change.getTargetBranch());
    entity.setLatestCommitSha(change.getLatestCommitSha());
    entity.setStatus(change.getStatus().name());
    entity.setCreatedAt(change.getCreatedAt());
    entity.setUpdatedAt(change.getUpdatedAt());
    return entity;
  }

  static Change toDomain(ChangeEntity entity) {
    return Change.restore(
        new ChangeId(entity.getId()),
        new RepositoryId(entity.getRepositoryId()),
        entity.getProviderChangeId(),
        entity.getTitle(),
        entity.getDescription(),
        entity.getAuthor(),
        entity.getSourceBranch(),
        entity.getTargetBranch(),
        entity.getLatestCommitSha(),
        Change.Status.valueOf(entity.getStatus()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}