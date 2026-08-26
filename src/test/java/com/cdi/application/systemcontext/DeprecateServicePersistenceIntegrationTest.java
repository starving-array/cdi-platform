package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.DeprecateServiceResult;
import com.cdi.application.port.in.DeprecateServiceCommand;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end P2 DeprecateService integration test: real
 * {@code ServiceRepository} JPA adapter (Testcontainers PostgreSQL)
 * wired into {@code DeprecateServiceHandler}. Verifies the ACTIVE→DEPRECATED
 * transition round-trips through persistence, repeat deprecation is rejected
 * after a real write, role enforcement holds, cross-tenant isolation is enforced,
 * and a missing service resolves to {@code SERVICE_NOT_FOUND}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class DeprecateServicePersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private DeprecateServiceHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeprecateServiceHandler(serviceRepository, CLOCK);
  }

  @Test
  void deprecatePersistsStatusTransition() {
    TenantId tenantId = seedOrganization("Deprecate Service Test Alpha");
    ServiceId serviceId = seedService(tenantId, "auth-service");

    DeprecateServiceResult result = handler.handle(command(tenantId, serviceId));

    assertEquals(serviceId, result.serviceId());
    assertEquals(Service.Status.DEPRECATED, result.status());

    Service reloaded = serviceRepository.findByTenantIdAndId(tenantId, serviceId).orElseThrow();
    assertEquals(Service.Status.DEPRECATED, reloaded.getStatus());
  }

  @Test
  void repeatedDeprecateIsRejectedAfterPersistence() {
    TenantId tenantId = seedOrganization("Deprecate Service Test Beta");
    ServiceId serviceId = seedService(tenantId, "billing-service");
    handler.handle(command(tenantId, serviceId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId, serviceId)));

    assertEquals(ApplicationError.SERVICE_ALREADY_DEPRECATED, ex.getError());
    Service reloaded = serviceRepository.findByTenantIdAndId(tenantId, serviceId).orElseThrow();
    assertEquals(Service.Status.DEPRECATED, reloaded.getStatus());
  }

  @Test
  void nonTenantAdminIsRejected() {
    TenantId tenantId = seedOrganization("Deprecate Service Test Gamma");
    ServiceId serviceId = seedService(tenantId, "order-service");
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, tenantId, serviceId)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    Service reloaded = serviceRepository.findByTenantIdAndId(tenantId, serviceId).orElseThrow();
    assertEquals(Service.Status.ACTIVE, reloaded.getStatus());
  }

  @Test
  void missingServiceRaisesServiceNotFound() {
    TenantId tenantId = seedOrganization("Deprecate Service Test Delta");
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId, ServiceId.generate())));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantServiceLookupFailsWithServiceNotFound() {
    TenantId tenantA = seedOrganization("Deprecate Service Tenant A");
    TenantId tenantB = seedOrganization("Deprecate Service Tenant B");
    ServiceId serviceIdA = seedService(tenantA, "core-service");

    // Tenant B attempts to deprecate Tenant A's service
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantB, serviceIdA)));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
    Service reloaded = serviceRepository.findByTenantIdAndId(tenantA, serviceIdA).orElseThrow();
    assertEquals(Service.Status.ACTIVE, reloaded.getStatus());
  }

  private TenantId seedOrganization(String name) {
    Organization organization = new Organization(TenantId.generate(), name, NOW);
    return organizationRepository.save(organization).getId();
  }

  private ServiceId seedService(TenantId tenantId, String name) {
    Service service = new Service(
        ServiceId.generate(), tenantId, name, CriticalityTier.TIER_1, "team-lead", NOW);
    return serviceRepository.save(service).getId();
  }

  private DeprecateServiceCommand command(TenantId tenantId, ServiceId serviceId) {
    return command(new Actor("admin-1", Actor.Role.TENANT_ADMIN), tenantId, serviceId);
  }

  private DeprecateServiceCommand command(Actor actor, TenantId tenantId, ServiceId serviceId) {
    return new DeprecateServiceCommand(tenantId, serviceId, actor);
  }
}