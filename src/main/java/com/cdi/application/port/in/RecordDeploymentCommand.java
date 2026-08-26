package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.DeploymentStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RecordDeploymentCommand(
    TenantId tenantId,
    ServiceId serviceId,
    String commitSha,
    String environment,
    DeploymentStatus status,
    String externalDeploymentId,
    Instant deployedAt,
    Actor actor) {

  public RecordDeploymentCommand {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(serviceId, "serviceId must not be null");
    Objects.requireNonNull(commitSha, "commitSha must not be null");
    Objects.requireNonNull(environment, "environment must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(deployedAt, "deployedAt must not be null");
    Objects.requireNonNull(actor, "actor must not be null");
  }

  public Optional<String> getExternalDeploymentId() {
    return Optional.ofNullable(externalDeploymentId);
  }
}