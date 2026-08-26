package com.cdi.deployment.adapter.out.persistence;

import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentOutcome;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.deployment.domain.OutcomeType;

public final class DeploymentMapper {

  private DeploymentMapper() {}

  public static Deployment toDomain(DeploymentEntity entity) {
    if (entity == null) return null;

    DeploymentOutcome outcome = null;
    if (entity.getOutcome() != null) {
      DeploymentOutcomeEntity oe = entity.getOutcome();
      outcome = new DeploymentOutcome(
          oe.getId(),
          new TenantId(oe.getTenantId()),
          new DeploymentId(entity.getId()),
          OutcomeType.valueOf(oe.getOutcome()),
          oe.getIncidentReference(),
          oe.getRecordedAt(),
          oe.getCreatedAt());
    }

    return new Deployment(
        new DeploymentId(entity.getId()),
        new TenantId(entity.getTenantId()),
        new ServiceId(entity.getServiceId()),
        entity.getCommitSha(),
        entity.getEnvironment(),
        DeploymentStatus.valueOf(entity.getStatus()),
        entity.getExternalDeploymentId(),
        entity.getDeployedAt(),
        entity.getCreatedAt(),
        outcome);
  }

  public static DeploymentEntity toEntity(Deployment domain) {
    if (domain == null) return null;

    DeploymentEntity entity = new DeploymentEntity(
        domain.getId().value(),
        domain.getTenantId().value(),
        domain.getServiceId().value(),
        domain.getCommitSha(),
        domain.getEnvironment(),
        domain.getStatus().name(),
        domain.getExternalDeploymentId().orElse(null),
        domain.getDeployedAt(),
        domain.getCreatedAt());

    if (domain.getOutcome().isPresent()) {
      DeploymentOutcome outcome = domain.getOutcome().get();
      DeploymentOutcomeEntity outcomeEntity = new DeploymentOutcomeEntity(
          outcome.getId(),
          outcome.getTenantId().value(),
          outcome.getOutcome().name(),
          outcome.getIncidentReference().orElse(null),
          outcome.getRecordedAt(),
          outcome.getCreatedAt());
      entity.setOutcome(outcomeEntity);
    }

    return entity;
  }
}