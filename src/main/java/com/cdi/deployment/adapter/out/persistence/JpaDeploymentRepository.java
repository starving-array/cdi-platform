package com.cdi.deployment.adapter.out.persistence;

import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaDeploymentRepository implements DeploymentRepository {

  private final DeploymentJpaRepository deploymentJpaRepository;

  public JpaDeploymentRepository(DeploymentJpaRepository deploymentJpaRepository) {
    this.deploymentJpaRepository = deploymentJpaRepository;
  }

  @Override
  @Transactional
  public Deployment save(Deployment deployment) {
    DeploymentEntity entity = DeploymentMapper.toEntity(deployment);
    DeploymentEntity saved = deploymentJpaRepository.saveAndFlush(entity);
    return DeploymentMapper.toDomain(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Deployment> findByTenantIdAndId(TenantId tenantId, DeploymentId id) {
    return deploymentJpaRepository.findByTenantIdAndId(tenantId.value(), id.value())
        .map(DeploymentMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Deployment> findByTenantIdAndExternalId(TenantId tenantId, String externalId) {
    return deploymentJpaRepository.findByTenantIdAndExternalDeploymentId(tenantId.value(), externalId)
        .map(DeploymentMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Deployment> listDeployments(
      TenantId tenantId, Optional<ServiceId> serviceId, Optional<String> environment) {
    UUID svcId = serviceId.map(ServiceId::value).orElse(null);
    String env = environment.orElse(null);
    return deploymentJpaRepository.listDeployments(tenantId.value(), svcId, env)
        .stream()
        .map(DeploymentMapper::toDomain)
        .toList();
  }
}