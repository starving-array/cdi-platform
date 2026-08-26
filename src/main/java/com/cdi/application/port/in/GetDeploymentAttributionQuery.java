package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.TenantId;

import java.util.Objects;

public record GetDeploymentAttributionQuery(
    TenantId tenantId,
    DeploymentId deploymentId,
    Actor actor) {

  public GetDeploymentAttributionQuery {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(deploymentId, "deploymentId must not be null");
    Objects.requireNonNull(actor, "actor must not be null");
  }
}