package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.result.CreateServiceResult;
import com.cdi.application.port.in.CreateServiceCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.systemcontext.adapter.out.persistence.ServiceJpaRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-09 integration test: real {@code ServiceRepository} JPA
 * adapter (Testcontainers PostgreSQL) wired into {@code CreateServiceHandler}.
 * UC-09 emits no domain event, so (like UC-08) there is no event-publisher
 * fake here. Verifies the full persist path, cross-process idempotent reuse,
 * and that duplicate natural keys never create a second service row
 * (application-layer.md §6/§10).
 *
 * <p>A real {@code Organization} (tenant row) is persisted before each test so
 * the V8 {@code FK(tenant_id) REFERENCES tenant(id)} constraint is satisfied.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class CreateServicePersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private ServiceJpaRepository serviceRowCount;

  private TenantId tenantId;
  private CreateServiceHandler handler;

  @BeforeEach
  void setUp() {
    tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "service-e2e-" + UUID.randomUUID(), Instant.now()));
    handler = new CreateServiceHandler(serviceRepository, CLOCK);
  }

  @Test
  void firstInvocationCreatesService() {
    CreateServiceCommand command = command("payment-service", "team-payments");

    CreateServiceResult result = handler.handle(command);

    assertTrue(result.created());
    assertEquals(
        1, serviceRowCount.countByTenantIdAndName(
            tenantId.value(), "payment-service"));

    Service persisted = serviceRepository.findByTenantIdAndName(
        tenantId, "payment-service").orElseThrow();
    assertEquals(result.serviceId(), persisted.getId());
    assertEquals(Service.Status.ACTIVE, persisted.getStatus());
    assertEquals(NOW, persisted.getCreatedAt());
  }

  @Test
  void secondInvocationReturnsExistingServiceWithCreatedFalse() {
    CreateServiceCommand command = command("payment-service", "team-payments");

    CreateServiceResult first = handler.handle(command);
    CreateServiceResult second = handler.handle(command);

    assertTrue(first.created());
    assertFalse(second.created());
    assertEquals(first.serviceId(), second.serviceId());
  }

  @Test
  void repeatedInvocationLeavesExactlyOneRow() {
    CreateServiceCommand command = command("payment-service", "team-payments");

    handler.handle(command);
    handler.handle(command);
    handler.handle(command);

    assertEquals(
        1, serviceRowCount.countByTenantIdAndName(
            tenantId.value(), "payment-service"));
  }

  private CreateServiceCommand command(String name, String owner) {
    return new CreateServiceCommand(
        tenantId, name, CriticalityTier.TIER_0, owner,
        new Actor("admin-1", Actor.Role.TENANT_ADMIN), new IdempotencyKey("header-key-1"));
  }
}
