package com.cdi.repository.adapter.out.persistence;

import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code RepositoryRepository} port (UC-08,
 * repository table, data-model.md §3, V7 migration).
 *
 * <p>Persistence-side only: no domain rules here. The tenant-scoped natural-key
 * lookup that drives UC-08 idempotent reuse delegates to the Spring Data
 * derived query; {@code UNIQUE (tenant_id, provider_type, external_id)}
 * (V7) remains the DB safety net. Failures bubble up as Spring
 * {@code DataAccessException}s per the documented persistence policy
 * (ports-and-adapters.md §4); concurrent duplicate insert violation surfaces
 * as {@code DataIntegrityViolationException} and is propagated verbatim (it is
 * not converted into a fake successful creation — UC-08 contract).
 */
@org.springframework.stereotype.Repository
public class JpaRepositoryRepository implements RepositoryRepository {

  private final RepositoryJpaRepository repositoryJpaRepository;

  public JpaRepositoryRepository(RepositoryJpaRepository repositoryJpaRepository) {
    this.repositoryJpaRepository = repositoryJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Repository> findByTenantIdProviderTypeExternalId(
      TenantId tenantId, Repository.ProviderType providerType, String externalId) {
    return repositoryJpaRepository
        .findByTenantIdAndProviderTypeAndExternalId(
            tenantId.value(), providerType.name(), externalId)
        .map(RepositoryMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Repository> findByTenantIdAndId(
      TenantId tenantId, RepositoryId repositoryId) {
    return repositoryJpaRepository
        .findByTenantIdAndId(tenantId.value(), repositoryId.value())
        .map(RepositoryMapper::toDomain);
  }

  @Override
  @Transactional
  public Repository save(Repository repository) {
    RepositoryEntity entity = RepositoryMapper.toEntity(repository);
    return RepositoryMapper.toDomain(repositoryJpaRepository.save(entity));
  }
}
