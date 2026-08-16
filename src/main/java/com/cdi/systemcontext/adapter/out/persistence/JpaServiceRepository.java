package com.cdi.systemcontext.adapter.out.persistence;

import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code ServiceRepository} port (UC-09
 * CreateService and UC-15 GetService, service table, data-model.md §3.A, V8
 * migration).
 *
 * <p>Persistence-side only: no domain rules here. The tenant-scoped natural-key
 * lookup that drives UC-09 idempotent reuse and the tenant-scoped by-id lookup
 * that serves UC-15 reads delegate to the Spring Data derived queries;
 * {@code UNIQUE (tenant_id, name)} (V8) remains the DB safety
 * net. Failures bubble up as Spring {@code DataAccessException}s per the
 * documented persistence policy (ports-and-adapters.md §4); concurrent
 * duplicate insert violation surfaces as
 * {@code DataIntegrityViolationException} and is propagated verbatim (it is
 * not converted into a fake successful creation — UC-09 contract).
 */
@org.springframework.stereotype.Repository
public class JpaServiceRepository implements ServiceRepository {

  private final ServiceJpaRepository serviceJpaRepository;

  public JpaServiceRepository(ServiceJpaRepository serviceJpaRepository) {
    this.serviceJpaRepository = serviceJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Service> findByTenantIdAndName(TenantId tenantId, String name) {
    return serviceJpaRepository
        .findByTenantIdAndName(tenantId.value(), name)
        .map(ServiceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Service> findByTenantIdAndId(
      TenantId tenantId, ServiceId serviceId) {
    return serviceJpaRepository
        .findByTenantIdAndId(tenantId.value(), serviceId.value())
        .map(ServiceMapper::toDomain);
  }

  @Override
  @Transactional
  public Service save(Service service) {
    ServiceEntity entity = ServiceMapper.toEntity(service);
    return ServiceMapper.toDomain(serviceJpaRepository.save(entity));
  }
}
