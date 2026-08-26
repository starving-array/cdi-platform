package com.cdi.application.port.out;

import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository {
  Deployment save(Deployment deployment);
  Optional<Deployment> findByTenantIdAndId(TenantId tenantId, DeploymentId id);
  Optional<Deployment> findByTenantIdAndExternalId(TenantId tenantId, String externalId);
  List<Deployment> listDeployments(TenantId tenantId, Optional<ServiceId> serviceId, Optional<String> environment);
}