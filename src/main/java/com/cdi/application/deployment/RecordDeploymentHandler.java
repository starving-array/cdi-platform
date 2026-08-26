package com.cdi.application.deployment;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.RecordDeploymentCommand;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.deployment.domain.Deployment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RecordDeploymentHandler {

  private final DeploymentRepository deploymentRepository;
  private final ServiceRepository serviceRepository;
  private final Clock clock;

  public RecordDeploymentHandler(
      DeploymentRepository deploymentRepository,
      ServiceRepository serviceRepository,
      Clock clock) {
    this.deploymentRepository = Objects.requireNonNull(deploymentRepository, "deploymentRepository");
    this.serviceRepository = Objects.requireNonNull(serviceRepository, "serviceRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public RecordDeploymentHandler(
      DeploymentRepository deploymentRepository,
      ServiceRepository serviceRepository) {
    this(deploymentRepository, serviceRepository, Clock.systemUTC());
  }

  public RecordDeploymentResult handle(RecordDeploymentCommand command) {
    // 1. Authorization: SYSTEM_WORKER and ENGINEER allowed
    Actor.Role role = command.actor().role();
    if (role != Actor.Role.SYSTEM_WORKER && role != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only SYSTEM_WORKER or ENGINEER can record deployments");
    }

    // 2. Validate Service exists in tenant
    serviceRepository.findByTenantIdAndId(command.tenantId(), command.serviceId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.SERVICE_NOT_FOUND, "Service not found in tenant"));

    // 3. Idempotency: check if externalDeploymentId already exists in tenant (D-DEP-4)
    if (command.getExternalDeploymentId().isPresent()) {
      Optional<Deployment> existing = deploymentRepository.findByTenantIdAndExternalId(
          command.tenantId(), command.getExternalDeploymentId().get());
      if (existing.isPresent()) {
        return new RecordDeploymentResult(existing.get(), false);
      }
    }

    // 4. Create and persist new Deployment
    Instant now = clock.instant();
    Deployment deployment = new Deployment(
        DeploymentId.generate(),
        command.tenantId(),
        command.serviceId(),
        command.commitSha(),
        command.environment(),
        command.status(),
        command.getExternalDeploymentId().orElse(null),
        command.deployedAt(),
        now);

    Deployment saved = deploymentRepository.save(deployment);
    return new RecordDeploymentResult(saved, true);
  }

  public record RecordDeploymentResult(Deployment deployment, boolean created) {}
}