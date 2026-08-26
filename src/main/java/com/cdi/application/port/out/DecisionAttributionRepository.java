package com.cdi.application.port.out;

import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.common.domain.id.AttributionId;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Optional;

public interface DecisionAttributionRepository {
  DecisionAttribution save(DecisionAttribution attribution);
  Optional<DecisionAttribution> findByTenantIdAndDeploymentId(TenantId tenantId, DeploymentId deploymentId);
  List<DecisionAttribution> findByTenantIdAndServiceId(TenantId tenantId, Optional<ServiceId> serviceId);
  AttributionSummary getSummary(TenantId tenantId, Optional<ServiceId> serviceId);
}