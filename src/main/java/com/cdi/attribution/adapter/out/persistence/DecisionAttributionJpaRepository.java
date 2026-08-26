package com.cdi.attribution.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionAttributionJpaRepository extends JpaRepository<DecisionAttributionEntity, UUID> {

  Optional<DecisionAttributionEntity> findByTenantIdAndDeploymentId(UUID tenantId, UUID deploymentId);

  @Query("SELECT a FROM DecisionAttributionEntity a "
      + "WHERE a.tenantId = :tenantId "
      + "AND (:serviceId IS NULL OR a.serviceId = :serviceId) "
      + "ORDER BY a.attributedAt DESC, a.id DESC")
  List<DecisionAttributionEntity> listByTenantAndService(
      @Param("tenantId") UUID tenantId,
      @Param("serviceId") UUID serviceId);
}