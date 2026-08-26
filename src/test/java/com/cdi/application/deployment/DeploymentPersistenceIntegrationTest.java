package com.cdi.application.deployment;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetDeploymentQuery;
import com.cdi.application.port.in.ListDeploymentsQuery;
import com.cdi.application.port.in.RecordDeploymentCommand;
import com.cdi.application.port.in.RecordDeploymentOutcomeCommand;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.organization.domain.Organization;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class DeploymentPersistenceIntegrationTest {

  @Autowired
  private RecordDeploymentHandler recordDeploymentHandler;

  @Autowired
  private RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler;

  @Autowired
  private GetDeploymentQueryService getDeploymentQueryService;

  @Autowired
  private ListDeploymentsQueryService listDeploymentsQueryService;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private DeploymentRepository deploymentRepository;

  private final Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
  private final Actor engineer = new Actor("eng-1", Actor.Role.ENGINEER);
  private final Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  @Test
  void recordsAndRetrievesDeploymentSuccessfully() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "auth-service");

    RecordDeploymentCommand cmd = new RecordDeploymentCommand(
        tenantId,
        service.getId(),
        "sha-auth-100",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-dep-1",
        Instant.parse("2026-08-16T12:00:00Z"),
        worker);

    RecordDeploymentHandler.RecordDeploymentResult res = recordDeploymentHandler.handle(cmd);
    assertTrue(res.created());
    assertNotNull(res.deployment().getId());

    Deployment fetched = getDeploymentQueryService.handle(
        new GetDeploymentQuery(tenantId, res.deployment().getId(), engineer));
    assertEquals("sha-auth-100", fetched.getCommitSha());
    assertEquals("production", fetched.getEnvironment());
    assertEquals(DeploymentStatus.IN_PROGRESS, fetched.getStatus());
  }

  @Test
  void idempotentReplayReturnsExistingDeploymentWithoutDuplicating() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "order-service");

    RecordDeploymentCommand cmd = new RecordDeploymentCommand(
        tenantId,
        service.getId(),
        "sha-order-200",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-order-dep-99",
        Instant.parse("2026-08-16T12:00:00Z"),
        worker);

    RecordDeploymentHandler.RecordDeploymentResult first = recordDeploymentHandler.handle(cmd);
    assertTrue(first.created());

    RecordDeploymentHandler.RecordDeploymentResult second = recordDeploymentHandler.handle(cmd);
    assertFalse(second.created(), "Replay must report created=false");
    assertEquals(first.deployment().getId(), second.deployment().getId());
  }

  @Test
  void recordsOutcomeAndEnforcesSingleOutcomeInvariant() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "billing-service");

    RecordDeploymentCommand cmd = new RecordDeploymentCommand(
        tenantId,
        service.getId(),
        "sha-bill-300",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-bill-1",
        Instant.parse("2026-08-16T12:00:00Z"),
        engineer);

    RecordDeploymentHandler.RecordDeploymentResult res = recordDeploymentHandler.handle(cmd);

    RecordDeploymentOutcomeCommand outcomeCmd = new RecordDeploymentOutcomeCommand(
        tenantId,
        res.deployment().getId(),
        OutcomeType.SUCCESS,
        null,
        Instant.parse("2026-08-16T12:15:00Z"),
        engineer);

    Deployment completed = recordDeploymentOutcomeHandler.handle(outcomeCmd);
    assertEquals(DeploymentStatus.SUCCESSFUL, completed.getStatus());
    assertTrue(completed.getOutcome().isPresent());

    // Duplicate outcome is rejected
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> recordDeploymentOutcomeHandler.handle(outcomeCmd));
    assertEquals(ApplicationError.OUTCOME_ALREADY_RECORDED, ex.getError());
  }

  @Test
  void strictTenantIsolationForDeployments() {
    TenantId tenantA = seedTenant();
    TenantId tenantB = seedTenant();

    Service serviceA = seedService(tenantA, "svc-a");
    Service serviceB = seedService(tenantB, "svc-b");

    RecordDeploymentCommand cmdA = new RecordDeploymentCommand(
        tenantA,
        serviceA.getId(),
        "sha-a",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-a",
        Instant.parse("2026-08-16T12:00:00Z"),
        worker);

    RecordDeploymentHandler.RecordDeploymentResult resA = recordDeploymentHandler.handle(cmdA);

    // Tenant B cannot access Tenant A deployment
    ApplicationException ex = assertThrows(ApplicationException.class, () ->
        getDeploymentQueryService.handle(new GetDeploymentQuery(tenantB, resA.deployment().getId(), engineer)));
    assertEquals(ApplicationError.DEPLOYMENT_NOT_FOUND, ex.getError());

    // Listing for Tenant B does not return Tenant A deployments
    List<Deployment> listB = listDeploymentsQueryService.handle(
        new ListDeploymentsQuery(tenantB, null, null, engineer));
    assertTrue(listB.isEmpty());
  }

  @Test
  void authorizationEnforcedOnDeploymentEndpoints() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "authz-service");

    RecordDeploymentCommand cmd = new RecordDeploymentCommand(
        tenantId,
        service.getId(),
        "sha-authz",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-authz",
        Instant.parse("2026-08-16T12:00:00Z"),
        admin); // Admin not authorized to write deployments

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> recordDeploymentHandler.handle(cmd));
    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private TenantId seedTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(tenantId, "Test Org " + tenantId.value(), Instant.now()));
    return tenantId;
  }

  private Service seedService(TenantId tenantId, String name) {
    Service service = new Service(
        ServiceId.generate(),
        tenantId,
        name,
        CriticalityTier.TIER_1,
        "team-core",
        Instant.parse("2026-08-16T10:00:00Z"));
    return serviceRepository.save(service);
  }
}