package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetServiceQuery;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for UC-15 GetService. Uses a fake port only at the application
 * boundary; no Testcontainers, no Spring context, no persistence is started.
 * Verifies the full aggregate read for both ENGINEER and TENANT_ADMIN actors,
 * deprecated-status preservation, missing-tenant and cross-tenant
 * {@code SERVICE_NOT_FOUND}, and SYSTEM_WORKER role rejection.
 */
class GetServiceQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakeServiceRepository serviceRepository;
  private GetServiceQueryService service;

  @BeforeEach
  void setUp() {
    serviceRepository = new FakeServiceRepository();
    service = new GetServiceQueryService(serviceRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    Service seeded = serviceRepository.seed(tenantId, "payment-service");

    Service result = service.handle(
        new GetServiceQuery(seeded.getId()), tenantId, engineer);

    assertEquals(seeded.getId(), result.getId());
    assertEquals(tenantId, result.getTenantId());
    assertEquals("payment-service", result.getName());
    assertEquals(CriticalityTier.TIER_0, result.getCriticality());
    assertEquals("team-payments", result.getOwner());
    assertEquals(Service.Status.ACTIVE, result.getStatus());
    assertEquals(seeded.getCreatedAt(), result.getCreatedAt());
    assertEquals(0, result.getDependencies().size());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    Service seeded = serviceRepository.seed(tenantId, "payment-service");

    Service result = service.handle(
        new GetServiceQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), result.getId());
    assertEquals("payment-service", result.getName());
  }

  @Test
  void preservesDeprecatedStatus() {
    Service seeded = serviceRepository.seed(tenantId, "payment-service");
    seeded.deprecate();

    Service result = service.handle(
        new GetServiceQuery(seeded.getId()), tenantId, engineer);

    assertEquals(Service.Status.DEPRECATED, result.getStatus());
  }

  @Test
  void missingServiceRaisesServiceNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetServiceQuery(ServiceId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantServiceIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    Service seeded = serviceRepository.seed(tenantB, "payment-service");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetServiceQuery(seeded.getId()), tenantId, engineer));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    Service seeded = serviceRepository.seed(tenantId, "payment-service");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetServiceQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private static class FakeServiceRepository implements ServiceRepository {
    final Map<ServiceId, Service> byId = new HashMap<>();

    Service seed(TenantId tenantId, String name) {
      Service svc = new Service(
          ServiceId.generate(), tenantId, name, CriticalityTier.TIER_0, "team-payments",
          Instant.parse("2026-08-14T12:00:00Z"));
      byId.put(svc.getId(), svc);
      return svc;
    }

    @Override
    public Optional<Service> findByTenantIdAndName(TenantId tenantId, String name) {
      return byId.values().stream()
          .filter(s -> s.getTenantId().equals(tenantId) && s.getName().equals(name))
          .findFirst();
    }

    @Override
    public Optional<Service> findByTenantIdAndId(TenantId tenantId, ServiceId serviceId) {
      return byId.values().stream()
          .filter(s -> s.getTenantId().equals(tenantId) && s.getId().equals(serviceId))
          .findFirst();
    }

    @Override
    public Service save(Service service) {
      byId.put(service.getId(), service);
      return service;
    }
  }
}