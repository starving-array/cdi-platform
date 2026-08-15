package com.cdi.change.adapter.out.persistence;

import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code ChangeRepository} port (UC-01, change
 * table, data-model.md §B).
 *
 * <p>Persistence-side only: no domain rules here. The natural-key lookup that
 * drives UC-01 duplicate detection delegates to the Spring Data derived query;
 * {@code UNIQUE (tenant_id, repository_id, provider_change_id)} remains the DB
 * safety net. Failures bubble up as Spring {@code DataAccessException}s per the
 * documented persistence policy (ports-and-adapters.md §4: DB down → webhook
 * 500); constraint violations surface as {@link DataIntegrityViolationException}.
 */
@Repository
public class JpaChangeRepository implements ChangeRepository {

  private final ChangeJpaRepository changeJpaRepository;

  public JpaChangeRepository(ChangeJpaRepository changeJpaRepository) {
    this.changeJpaRepository = changeJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Change> findByTenantAndRepositoryAndProvider(
      TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
    return changeJpaRepository
        .findByTenantIdAndRepositoryIdAndProviderChangeId(
            tenantId.value(), repositoryId.value(), providerChangeId)
        .map(ChangeMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
    return changeJpaRepository
        .findByTenantIdAndId(tenantId.value(), changeId.value())
        .map(ChangeMapper::toDomain);
  }

  @Override
  @Transactional
  public Change save(TenantId tenantId, Change change) {
    ChangeEntity entity = ChangeMapper.toEntity(tenantId, change);
    return ChangeMapper.toDomain(changeJpaRepository.save(entity));
  }
}