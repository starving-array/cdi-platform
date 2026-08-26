package com.cdi.attribution.adapter.out.persistence;

import com.cdi.application.port.out.DecisionAttributionRepository;
import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.common.domain.id.AttributionId;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaDecisionAttributionRepository implements DecisionAttributionRepository {

  private final DecisionAttributionJpaRepository jpaRepository;

  public JpaDecisionAttributionRepository(DecisionAttributionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  @Transactional
  public DecisionAttribution save(DecisionAttribution attribution) {
    DecisionAttributionEntity entity = DecisionAttributionMapper.toEntity(attribution);
    DecisionAttributionEntity saved = jpaRepository.saveAndFlush(entity);
    return DecisionAttributionMapper.toDomain(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DecisionAttribution> findByTenantIdAndDeploymentId(TenantId tenantId, DeploymentId deploymentId) {
    return jpaRepository.findByTenantIdAndDeploymentId(tenantId.value(), deploymentId.value())
        .map(DecisionAttributionMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DecisionAttribution> findByTenantIdAndServiceId(TenantId tenantId, Optional<ServiceId> serviceId) {
    UUID svcId = serviceId.map(ServiceId::value).orElse(null);
    return jpaRepository.listByTenantAndService(tenantId.value(), svcId)
        .stream()
        .map(DecisionAttributionMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AttributionSummary getSummary(TenantId tenantId, Optional<ServiceId> serviceId) {
    UUID svcId = serviceId.map(ServiceId::value).orElse(null);
    List<DecisionAttributionEntity> list = jpaRepository.listByTenantAndService(tenantId.value(), svcId);

    long total = list.size();
    long accurateLow = 0;
    long accurateHigh = 0;
    long underestimated = 0;
    long overestimated = 0;
    long unattributed = 0;

    for (DecisionAttributionEntity e : list) {
      AttributionClassification c = AttributionClassification.valueOf(e.getClassification());
      switch (c) {
        case ACCURATE_LOW_RISK -> accurateLow++;
        case ACCURATE_HIGH_RISK -> accurateHigh++;
        case UNDERESTIMATED_RISK -> underestimated++;
        case OVERESTIMATED_RISK -> overestimated++;
        case UNATTRIBUTED -> unattributed++;
      }
    }

    long evaluatedCount = accurateLow + accurateHigh + underestimated + overestimated;
    long accurateCount = accurateLow + accurateHigh;
    double accuracyRate = evaluatedCount > 0 ? (double) accurateCount / evaluatedCount : 0.0;

    return new AttributionSummary(
        tenantId,
        serviceId.orElse(null),
        total,
        accurateLow,
        accurateHigh,
        underestimated,
        overestimated,
        unattributed,
        accuracyRate);
  }
}