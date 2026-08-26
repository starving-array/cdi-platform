package com.cdi.application.deployment;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetDeploymentQuery;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.deployment.domain.Deployment;

import java.util.Objects;

public final class GetDeploymentQueryService {

  private final DeploymentRepository deploymentRepository;

  public GetDeploymentQueryService(DeploymentRepository deploymentRepository) {
    this.deploymentRepository = Objects.requireNonNull(deploymentRepository, "deploymentRepository");
  }

  public Deployment handle(GetDeploymentQuery query) {
    Actor.Role role = query.actor().role();
    if (role != Actor.Role.ENGINEER && role != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only ENGINEER or TENANT_ADMIN can view deployments");
    }

    return deploymentRepository.findByTenantIdAndId(query.tenantId(), query.deploymentId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
  }
}