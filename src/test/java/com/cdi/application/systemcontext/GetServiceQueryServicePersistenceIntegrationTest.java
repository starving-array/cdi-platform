package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetServiceQuery;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end UC-15 GetService integration test: real JPA adapter
 * (Testcontainers PostgreSQL) wired into {@link GetServiceQueryService}.
 * Persists the tenant root (Organization, satisfying the V8 FK) and the
 * Service through the existing ports, then verifies the full aggregate read
 * for both ENGINEER and TENANT_ADMIN actors, deprecated-status round-trip (via
 * {@link Service#restore}), missing-tenant and cross-tenant
 * {@code SERVICE_NOT_FOUND}, and SYSTEM_WORKER role rejection
 * (application-layer.md §6 UC-15, data-model.md §3.A). Each test seeds its own
 * tenant id and a unique service name to stay isolated inside the shared
 * (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetServiceQueryServicePersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-14T12:00:00Z");

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private GetServiceQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetServiceQueryService(serviceRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    TenantId tenantId = seedTenant("GetSvc Alpha");
    Service seeded = seedService(tenantId, "payment-service");

    Service result = service.handle(
        new GetServiceQuery(seeded.getId()), tenantId, engineer);

    assertEquals(seeded.getId(), result.getId());
    assertEquals(tenantId, result.getTenantId());
    assertEquals("payment-service", result.getName());
    assertEquals(CriticalityTier.TIER_0, result.getCriticality());
    assertEquals("team-payments", result.getOwner());
    assertEquals(Service.Status.ACTIVE, result.getStatus());
    assertEquals(CREATED_AT, result.getCreatedAt());
    assertEquals(0, result.getDependencies().size());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    TenantId tenantId = seedTenant("GetSvc Beta");
    Service seeded = seedService(tenantId, "payment-service");

    Service result = service.handle(
        new GetServiceQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), result.getId());
    assertEquals("payment-service", result.getName());
  }

  @Test
  void preservesDeprecatedStatus() {
    TenantId tenantId = seedTenant("GetSvc Gamma");
    Service deprecated = Service.restore(
        ServiceId.generate(), tenantId, "legacy-service", CriticalityTier.TIER_0,
        "team-legacy", Service.Status.DEPRECATED, CREATED_AT);
    serviceRepository.save(deprecated);

    Service result = service.handle(
        new GetServiceQuery(deprecated.getId()), tenantId, engineer);

    assertEquals(Service.Status.DEPRECATED, result.getStatus());
  }

  @Test
  void missingServiceRaisesServiceNotFound() {
    TenantId tenantId = seedTenant("GetSvc Delta");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetServiceQuery(ServiceId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantServiceIsNotVisible() {
    TenantId tenantId = seedTenant("GetSvc Epsilon");
    Service seeded = seedService(tenantId, "payment-service");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetServiceQuery(seeded.getId()), TenantId.generate(), tenantAdmin));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    TenantId tenantId = seedTenant("GetSvc Zeta");
    Service seeded = seedService(tenantId, "payment-service");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetServiceQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private TenantId seedTenant(String name) {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, name + "-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }

  private Service seedService(TenantId tenantId, String name) {
    Service service = new Service(
        ServiceId.generate(), tenantId, name, CriticalityTier.TIER_0, "team-payments",
        CREATED_AT);
    return serviceRepository.save(service);
  }
}