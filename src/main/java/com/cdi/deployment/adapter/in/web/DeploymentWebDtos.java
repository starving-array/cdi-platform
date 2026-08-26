package com.cdi.deployment.adapter.in.web;

import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentOutcome;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.deployment.domain.OutcomeType;

import java.time.Instant;
import java.util.UUID;

public final class DeploymentWebDtos {

  private DeploymentWebDtos() {}

  public record RecordDeploymentRequest(
      UUID serviceId,
      String commitSha,
      String environment,
      DeploymentStatus status,
      String externalDeploymentId,
      Instant deployedAt) {}

  public record RecordOutcomeRequest(
      OutcomeType outcome,
      String incidentReference,
      Instant recordedAt) {}

  public record OutcomeDto(
      UUID id,
      OutcomeType outcome,
      String incidentReference,
      Instant recordedAt,
      Instant createdAt) {

    public static OutcomeDto fromDomain(DeploymentOutcome domain) {
      if (domain == null) return null;
      return new OutcomeDto(
          domain.getId(),
          domain.getOutcome(),
          domain.getIncidentReference().orElse(null),
          domain.getRecordedAt(),
          domain.getCreatedAt());
    }
  }

  public record DeploymentDto(
      UUID id,
      UUID tenantId,
      UUID serviceId,
      String commitSha,
      String environment,
      DeploymentStatus status,
      String externalDeploymentId,
      Instant deployedAt,
      Instant createdAt,
      OutcomeDto outcome) {

    public static DeploymentDto fromDomain(Deployment domain) {
      if (domain == null) return null;
      return new DeploymentDto(
          domain.getId().value(),
          domain.getTenantId().value(),
          domain.getServiceId().value(),
          domain.getCommitSha(),
          domain.getEnvironment(),
          domain.getStatus(),
          domain.getExternalDeploymentId().orElse(null),
          domain.getDeployedAt(),
          domain.getCreatedAt(),
          domain.getOutcome().map(OutcomeDto::fromDomain).orElse(null));
    }
  }

  public record RecordDeploymentResponse(
      DeploymentDto deployment,
      boolean created) {}
}