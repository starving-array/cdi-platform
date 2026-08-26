package com.cdi.application.port.out;

import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound persistence port for the Change aggregate, used by UC-01
 * ProposeChange, UC-02 RequestAnalysis, and P2 ListChanges (application-layer.md
 * §6, §10). Adapters persist to the {@code change} table (data-model.md §B),
 * scoped by {@code tenant_id}.
 *
 * <p>Only the operations genuinely required are declared: the natural-key
 * lookup and tenant-scoped by-id lookup that drive duplicate detection and
 * re-analysis resolution, tenant-scoped list queries (ordered
 * {@code createdAt DESC, id DESC}), plus the insert. No JPA, Spring Data,
 * SQL, or PostgreSQL types appear on this contract.
 */
public interface ChangeRepository {

  Optional<Change> findByTenantAndRepositoryAndProvider(
      TenantId tenantId, RepositoryId repositoryId, String providerChangeId);

  Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId);

  default List<Change> findAllByTenantId(TenantId tenantId) {
    return List.of();
  }

  default List<Change> findAllByTenantIdAndRepositoryId(
      TenantId tenantId, RepositoryId repositoryId) {
    return List.of();
  }

  Change save(TenantId tenantId, Change change);
}