package com.cdi.policy.adapter.out.persistence;

import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code PolicyRepository} port (UC-05 read,
 * UC-10 write; UC-16 tenant-scoped primary lookup; policy + policy_rule
 * tables, data-model.md §E, V9 migration).
 *
 * <p>Persistence-side only: no domain rules here. The tenant-scoped
 * active-policy lookup that drives UC-05 evaluation and UC-10 idempotent reuse
 * delegates to the Spring Data derived query. {@code UNIQUE (tenant_id)
 * WHERE status = 'ACTIVE'} (V9) remains the DB backstop for the
 * one-active-policy-per-tenant invariant (resolved audit Decision 1); an
 * attempt to insert a second active policy for a tenant surfaces as a Spring
 * {@code DataIntegrityViolationException} that is propagated verbatim
 * (UC-08/UC-09 precedent — never converted into a fake successful creation).
 *
 * <p>The Policy aggregate carries its own {@code TenantId}, so {@link #save}
 * reads the tenant scope from the aggregate itself (mirrors
 * {@code JpaServiceRepository}.
 */
@Repository
public class JpaPolicyRepository implements PolicyRepository {

  private final PolicyJpaRepository policyJpaRepository;

  public JpaPolicyRepository(PolicyJpaRepository policyJpaRepository) {
    this.policyJpaRepository = policyJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Policy> findActiveByTenant(TenantId tenantId) {
    return policyJpaRepository
        .findByTenantIdAndStatus(tenantId.value(), PolicyStatus.ACTIVE.name())
        .map(PolicyMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Policy> findByTenantIdAndId(TenantId tenantId, PolicyId policyId) {
    return policyJpaRepository
        .findByTenantIdAndId(tenantId.value(), policyId.value())
        .map(PolicyMapper::toDomain);
  }

  @Override
  @Transactional
  public Policy save(Policy policy) {
    PolicyEntity entity = PolicyMapper.toEntity(policy);
    return PolicyMapper.toDomain(policyJpaRepository.save(entity));
  }
}
