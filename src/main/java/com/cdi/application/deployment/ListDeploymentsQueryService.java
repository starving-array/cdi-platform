package com.cdi.application.deployment;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListDeploymentsQuery;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.deployment.domain.Deployment;

import java.util.List;
import java.util.Objects;

public final class ListDeploymentsQueryService {

  private final DeploymentRepository deploymentRepository;

  public ListDeploymentsQueryService(DeploymentRepository deploymentRepository) {
    this.deploymentRepository = Objects.requireNonNull(deploymentRepository, "deploymentRepository");
  }

  public List<Deployment> handle(ListDeploymentsQuery query) {
    Actor.Role role = query.actor().role();
    if (role != Actor.Role.ENGINEER && role != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only ENGINEER or TENANT_ADMIN can list deployments");
    }

    return deploymentRepository.listDeployments(query.tenantId(), query.getServiceId(), query.getEnvironment());
  }
}