package com.cdi.deployment.domain;

import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentTest {

  @Test
  void createsDeploymentWithInitialState() {
    TenantId tenantId = TenantId.generate();
    ServiceId serviceId = ServiceId.generate();
    Instant now = Instant.parse("2026-08-16T12:00:00Z");

    Deployment deployment = new Deployment(
        DeploymentId.generate(),
        tenantId,
        serviceId,
        "commit-sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "deploy-ext-1",
        now,
        now);

    assertEquals(DeploymentStatus.IN_PROGRESS, deployment.getStatus());
    assertEquals("commit-sha-123", deployment.getCommitSha());
    assertEquals("production", deployment.getEnvironment());
    assertEquals("deploy-ext-1", deployment.getExternalDeploymentId().orElse(null));
    assertFalse(deployment.getOutcome().isPresent());
  }

  @Test
  void recordsOutcomeAndUpdatesStatusToSuccessful() {
    TenantId tenantId = TenantId.generate();
    ServiceId serviceId = ServiceId.generate();
    Instant now = Instant.parse("2026-08-16T12:00:00Z");

    Deployment deployment = new Deployment(
        DeploymentId.generate(),
        tenantId,
        serviceId,
        "commit-sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "deploy-ext-1",
        now,
        now);

    deployment.recordOutcome(OutcomeType.SUCCESS, null, now.plusSeconds(300), now.plusSeconds(300));

    assertEquals(DeploymentStatus.SUCCESSFUL, deployment.getStatus());
    assertTrue(deployment.getOutcome().isPresent());
    assertEquals(OutcomeType.SUCCESS, deployment.getOutcome().get().getOutcome());
    assertFalse(deployment.getOutcome().get().getIncidentReference().isPresent());
  }

  @Test
  void recordsOutcomeAndUpdatesStatusToFailedWithIncident() {
    TenantId tenantId = TenantId.generate();
    ServiceId serviceId = ServiceId.generate();
    Instant now = Instant.parse("2026-08-16T12:00:00Z");

    Deployment deployment = new Deployment(
        DeploymentId.generate(),
        tenantId,
        serviceId,
        "commit-sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "deploy-ext-1",
        now,
        now);

    deployment.recordOutcome(OutcomeType.INCIDENT, "INC-9999", now.plusSeconds(600), now.plusSeconds(600));

    assertEquals(DeploymentStatus.FAILED, deployment.getStatus());
    assertTrue(deployment.getOutcome().isPresent());
    assertEquals(OutcomeType.INCIDENT, deployment.getOutcome().get().getOutcome());
    assertEquals("INC-9999", deployment.getOutcome().get().getIncidentReference().orElse(null));
  }

  @Test
  void duplicateOutcomeThrowsIllegalStateException() {
    TenantId tenantId = TenantId.generate();
    ServiceId serviceId = ServiceId.generate();
    Instant now = Instant.parse("2026-08-16T12:00:00Z");

    Deployment deployment = new Deployment(
        DeploymentId.generate(),
        tenantId,
        serviceId,
        "commit-sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "deploy-ext-1",
        now,
        now);

    deployment.recordOutcome(OutcomeType.SUCCESS, null, now, now);

    assertThrows(IllegalStateException.class, () ->
        deployment.recordOutcome(OutcomeType.ROLLED_BACK, "Rollback", now.plusSeconds(100), now.plusSeconds(100)));
  }
}