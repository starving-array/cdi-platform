package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreateServiceResult;
import com.cdi.application.port.in.CreateServiceCommand;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-09 CreateService. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. UC-09 emits no domain event, so (like UC-08) there is no
 * event-publisher fake here.
 */
class CreateServiceHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  // Fresh per test method (JUnit Jupiter creates a new test instance per
  // method); all command(...) helpers below use this same tenantId so that
  // idempotency is exercised within one tenant scope.
  private final TenantId tenantId = TenantId.generate();

  private FakeServiceRepository serviceRepository;
  private CreateServiceHandler handler;

  @BeforeEach
  void setUp() {
    serviceRepository = new FakeServiceRepository();
    handler = new CreateServiceHandler(serviceRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminCreation() {
    CreateServiceResult result = handler.handle(command("payment-service", "team-payments"));

    assertTrue(result.created());
    assertNotNull(result.serviceId());
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void generatedServiceIdIsReturned() {
    CreateServiceResult result = handler.handle(command("payment-service", "team-payments"));

    Service saved = serviceRepository.savedServices.get(0);
    assertEquals(saved.getId(), result.serviceId());
    assertNotNull(result.serviceId().value());
  }

  @Test
  void createdServiceStartsActive() {
    handler.handle(command("payment-service", "team-payments"));

    Service saved = serviceRepository.savedServices.get(0);
    assertEquals(Service.Status.ACTIVE, saved.getStatus());
  }

  @Test
  void allFieldsPreservedOnCreation() {
    CreateServiceCommand command = command("payment-service", "team-payments");
    handler.handle(command);

    Service saved = serviceRepository.savedServices.get(0);
    assertEquals(command.tenantId(), saved.getTenantId());
    assertEquals("payment-service", saved.getName());
    assertEquals(CriticalityTier.TIER_0, saved.getCriticality());
    assertEquals("team-payments", saved.getOwner());
    assertEquals(Service.Status.ACTIVE, saved.getStatus());
    assertEquals(NOW, saved.getCreatedAt());
    assertNull(saved.getRepositoryId());
    assertTrue(saved.getDependencies().isEmpty());
  }

  @Test
  void unauthorizedActorRejected() {
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    CreateServiceCommand engineerCommand = command(engineer, "payment-service", "team-payments");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(engineerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(serviceRepository.savedServices.isEmpty());
  }

  @Test
  void systemWorkerRoleRejected() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    CreateServiceCommand workerCommand = command(worker, "payment-service", "team-payments");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(workerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void invalidCommandRejectedByContract() {
    // blank name
    assertThrows(DomainException.class, () -> command("  ", "team-payments"));
    // null tenant
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        null, "name", CriticalityTier.TIER_0, "owner", tenantAdmin,
        new IdempotencyKey("key-1")));
    // null criticalityTier
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", null, "owner", tenantAdmin, new IdempotencyKey("key-1")));
    // null actor
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", CriticalityTier.TIER_0, "owner", null,
        new IdempotencyKey("key-1")));
    // null idempotencyKey
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", CriticalityTier.TIER_0, "owner", tenantAdmin, null));
  }

  @Test
  void duplicateServiceReusedIdempotently() {
    Service existing = serviceRepository.seed(
        tenantId, "payment-service");

    CreateServiceResult result = handler.handle(command("payment-service", "team-payments"));

    assertFalse(result.created());
    assertEquals(existing.getId(), result.serviceId());
    assertTrue(serviceRepository.savedServices.isEmpty());
  }

  @Test
  void repeatedInvocationDoesNotSave() {
    handler.handle(command("payment-service", "team-payments"));
    assertEquals(1, serviceRepository.savedServices.size());

    handler.handle(command("payment-service", "team-payments"));
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void differentNaturalKeyCreatesDistinctServices() {
    handler.handle(command("payment-service", "team-payments"));
    handler.handle(command("order-service", "team-orders"));

    assertEquals(2, serviceRepository.savedServices.size());
  }

  @Test
  void duplicateLookupIsTenantScoped() {
    // Same name under a different tenant does NOT match — it is a distinct
    // service, not an idempotent reuse.
    TenantId otherTenant = TenantId.generate();
    serviceRepository.seed(otherTenant, "payment-service");

    CreateServiceResult result = handler.handle(command("payment-service", "team-payments"));

    assertTrue(result.created());
    assertEquals(1, serviceRepository.savedServices.size());
  }

  @Test
  void persistenceFailurePropagates() {
    serviceRepository.throwOnSave = true;

    assertThrows(RuntimeException.class,
        () -> handler.handle(command("payment-service", "team-payments")));
  }

  private CreateServiceCommand command(String name, String owner) {
    return command(tenantAdmin, name, owner);
  }

  private CreateServiceCommand command(Actor actor, String name, String owner) {
    return new CreateServiceCommand(
        tenantId, name, CriticalityTier.TIER_0, owner, actor,
        new IdempotencyKey("header-key-1"));
  }

  private static class FakeServiceRepository implements ServiceRepository {
    final Map<String, Service> services = new HashMap<>();
    final List<Service> savedServices = new ArrayList<>();
    boolean throwOnSave = false;

    @Override
    public Optional<Service> findByTenantIdAndName(TenantId tenantId, String name) {
      return Optional.ofNullable(services.get(key(tenantId, name)));
    }

    @Override
    public Service save(Service service) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedServices.add(service);
      services.put(
          key(service.getTenantId(), service.getName()),
          service);
      return service;
    }

    Service seed(TenantId tenantId, String name) {
      Service svc = new Service(
          ServiceId.generate(), tenantId, name, CriticalityTier.TIER_0, "seeded",
          NOW.minusSeconds(3600));
      services.put(key(tenantId, name), svc);
      return svc;
    }

    private static String key(TenantId tenantId, String name) {
      return tenantId.value() + "|" + name;
    }
  }
}
