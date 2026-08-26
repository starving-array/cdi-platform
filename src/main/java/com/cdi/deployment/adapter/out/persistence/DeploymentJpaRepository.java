package com.cdi.deployment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentJpaRepository extends JpaRepository<DeploymentEntity, UUID> {

  Optional<DeploymentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<DeploymentEntity> findByTenantIdAndExternalDeploymentId(UUID tenantId, String externalDeploymentId);

  @Query("SELECT d FROM DeploymentEntity d "
      + "WHERE d.tenantId = :tenantId "
      + "AND (:serviceId IS NULL OR d.serviceId = :serviceId) "
      + "AND (:environment IS NULL OR d.environment = :environment) "
      + "ORDER BY d.deployedAt DESC, d.id DESC")
  List<DeploymentEntity> listDeployments(
      @Param("tenantId") UUID tenantId,
      @Param("serviceId") UUID serviceId,
      @Param("environment") String environment);
}