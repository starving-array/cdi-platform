package com.cdi.application.port.out;

import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.util.Optional;

/**
 * Outbound persistence port for the Change aggregate, used by UC-01
 * ProposeChange and UC-02 RequestAnalysis (application-layer.md §6, §10).
 * Adapters persist to the {@code change} table (data-model.md §B), scoped by
 * {@code tenant_id}.
 *
 * <p>Only the operations genuinely required by UC-01/UC-02 are declared:
 * the natural-key lookup and the tenant-scoped by-id lookup that drive
 * duplicate detection and re-analysis resolution, plus the insert. No JPA,
 * Spring Data, SQL, or PostgreSQL types appear on this contract.
 */
public interface ChangeRepository {

  Optional<Change> findByTenantAndRepositoryAndProvider(
      TenantId tenantId, RepositoryId repositoryId, String providerChangeId);

  Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId);

  Change save(TenantId tenantId, Change change);
}