package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.OutcomeType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RecordDeploymentOutcomeCommand(
    TenantId tenantId,
    DeploymentId deploymentId,
    OutcomeType outcome,
    String incidentReference,
    Instant recordedAt,
    Actor actor) {

  public RecordDeploymentOutcomeCommand {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(deploymentId, "deploymentId must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    Objects.requireNonNull(actor, "actor must not be null");
  }

  public Optional<String> getIncidentReference() {
    return Optional.ofNullable(incidentReference);
  }
}