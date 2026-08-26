package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.DeprecateServiceResult;
import com.cdi.application.port.in.DeprecateServiceCommand;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the P2 DeprecateService capability. Uses fake ports only at
 * the application boundary; no Testcontainers, no Spring context, no
 * persistence is started.
 */
class DeprecateServiceHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakeServiceRepository serviceRepository;
  private DeprecateServiceHandler handler;

  @BeforeEach
  void setUp() {
    serviceRepository = new FakeServiceRepository();
    handler = new DeprecateServiceHandler(serviceRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminDeprecateTransitionsActiveToDeprecated() {
    Service service = new Service(
        ServiceId.generate(), tenantId, "payment-service",
        CriticalityTier.TIER_0, "payments-team", NOW.minusSeconds(3600));
    serviceRepository.save(service);

    DeprecateServiceResult result = handler.handle(command(service.getId()));

    assertEquals(service.getId(), result.serviceId());
    assertEquals(Service.Status.DEPRECATED, result.status());
    assertEquals(2, serviceRepository.savedServices.size());
    assertEquals(Service.Status.DEPRECATED,
        serviceRepository.savedServices.get(1).getStatus());
  }

  @Test
  void unauthorizedActorRejected() {
    Service service = new Service(
        ServiceId.generate(), tenantId, "payment-service",
        CriticalityTier.TIER_0, "payments-team", NOW.minusSeconds(3600));
    serviceRepository.save(service);

    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, service.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void systemWorkerRoleRejected() {
    Service service = new Service(
        ServiceId.generate(), tenantId, "payment-service",
        CriticalityTier.TIER_0, "payments-team", NOW.minusSeconds(3600));
    serviceRepository.save(service);

    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(worker, service.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void missingServiceRaisesServiceNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(ServiceId.generate())));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
    assertTrue(serviceRepository.savedServices.isEmpty());
  }

  @Test
  void crossTenantServiceLookupFailsWithServiceNotFound() {
    TenantId otherTenant = TenantId.generate();
    Service service = new Service(
        ServiceId.generate(), otherTenant, "payment-service",
        CriticalityTier.TIER_0, "payments-team", NOW.minusSeconds(3600));
    serviceRepository.save(service);

    // Call with tenantId != otherTenant
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(new DeprecateServiceCommand(tenantId, service.getId(), tenantAdmin)));

    assertEquals(ApplicationError.SERVICE_NOT_FOUND, ex.getError());
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void alreadyDeprecatedServiceRaisesAlreadyDeprecated() {
    ServiceId serviceId = ServiceId.generate();
    Service service = Service.restore(
        serviceId, tenantId, "payment-service", CriticalityTier.TIER_0,
        "payments-team", Service.Status.DEPRECATED, NOW.minusSeconds(3600));
    serviceRepository.save(service);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(serviceId)));

    assertEquals(ApplicationError.SERVICE_ALREADY_DEPRECATED, ex.getError());
    assertEquals("already-deprecated", ex.getDetails().get("reason"));
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void persistenceFailurePropagates() {
    Service service = new Service(
        ServiceId.generate(), tenantId, "payment-service",
        CriticalityTier.TIER_0, "payments-team", NOW.minusSeconds(3600));
    serviceRepository.save(service);
    serviceRepository.throwOnSave = true;

    assertThrows(RuntimeException.class, () -> handler.handle(command(service.getId())));
  }

  private DeprecateServiceCommand command(ServiceId serviceId) {
    return command(tenantAdmin, serviceId);
  }

  private DeprecateServiceCommand command(Actor actor, ServiceId serviceId) {
    return new DeprecateServiceCommand(tenantId, serviceId, actor);
  }

  private static class FakeServiceRepository implements ServiceRepository {
    final Map<String, Service> services = new HashMap<>();
    final java.util.List<Service> savedServices = new java.util.ArrayList<>();
    boolean throwOnSave = false;

    private String key(TenantId tId, ServiceId sId) {
      return tId.value() + ":" + sId.value();
    }

    @Override
    public Optional<Service> findByTenantIdAndName(TenantId tenantId, String name) {
      return services.values().stream()
          .filter(s -> s.getTenantId().equals(tenantId) && s.getName().equals(name))
          .findFirst();
    }

    @Override
    public Optional<Service> findByTenantIdAndId(TenantId tenantId, ServiceId serviceId) {
      return Optional.ofNullable(services.get(key(tenantId, serviceId)));
    }

    @Override
    public Service save(Service service) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedServices.add(service);
      services.put(key(service.getTenantId(), service.getId()), service);
      return service;
    }
  }
}