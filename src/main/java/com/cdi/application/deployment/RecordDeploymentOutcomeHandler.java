package com.cdi.application.deployment;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.RecordDeploymentOutcomeCommand;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.deployment.domain.Deployment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RecordDeploymentOutcomeHandler {

  private final DeploymentRepository deploymentRepository;
  private final Clock clock;

  public RecordDeploymentOutcomeHandler(
      DeploymentRepository deploymentRepository,
      Clock clock) {
    this.deploymentRepository = Objects.requireNonNull(deploymentRepository, "deploymentRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public RecordDeploymentOutcomeHandler(
      DeploymentRepository deploymentRepository) {
    this(deploymentRepository, Clock.systemUTC());
  }

  public Deployment handle(RecordDeploymentOutcomeCommand command) {
    // 1. Authorization: SYSTEM_WORKER and ENGINEER allowed
    Actor.Role role = command.actor().role();
    if (role != Actor.Role.SYSTEM_WORKER && role != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only SYSTEM_WORKER or ENGINEER can record deployment outcomes");
    }

    // 2. Load Deployment in tenant
    Deployment deployment = deploymentRepository.findByTenantIdAndId(command.tenantId(), command.deploymentId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.DEPLOYMENT_NOT_FOUND, "Deployment not found"));

    // 3. Record Outcome
    Instant now = clock.instant();
    try {
      deployment.recordOutcome(
          command.outcome(),
          command.getIncidentReference().orElse(null),
          command.recordedAt(),
          now);
    } catch (IllegalStateException e) {
      throw new ApplicationException(ApplicationError.OUTCOME_ALREADY_RECORDED, e.getMessage());
    }

    return deploymentRepository.save(deployment);
  }
}