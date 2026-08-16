package com.cdi.organization.adapter.out.persistence;

import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code OrganizationRepository} port (UC-07,
 * tenant table, data-model.md §3.A).
 *
 * <p>Persistence-side only: no domain rules here. The natural-key lookup
 * that drives UC-07 idempotent reuse delegates to the Spring Data derived
 * query; {@code UNIQUE(name)} remains the DB safety net. Failures bubble up
 * as Spring {@code DataAccessException}s per the documented persistence
 * policy (ports-and-adapters.md §4); concurrent duplicate insert violation
 * surfaces as {@link DataIntegrityViolationException}.
 */
@Repository
public class JpaOrganizationRepository implements OrganizationRepository {

  private final OrganizationJpaRepository organizationJpaRepository;

  public JpaOrganizationRepository(OrganizationJpaRepository organizationJpaRepository) {
    this.organizationJpaRepository = organizationJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Organization> findByName(String name) {
    return organizationJpaRepository.findByName(name)
        .map(OrganizationMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Organization> findById(TenantId tenantId) {
    return organizationJpaRepository.findById(tenantId.value())
        .map(OrganizationMapper::toDomain);
  }

  @Override
  @Transactional
  public Organization save(Organization organization) {
    OrganizationEntity entity = OrganizationMapper.toEntity(organization);
    return OrganizationMapper.toDomain(organizationJpaRepository.save(entity));
  }
}
