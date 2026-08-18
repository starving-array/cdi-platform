package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.SuspendOrganizationResult;
import com.cdi.application.port.in.SuspendOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
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
 * End-to-end P2 SuspendOrganization integration test: real
 * {@code OrganizationRepository} JPA adapter (Testcontainers PostgreSQL)
 * wired into {@code SuspendOrganizationHandler}. Verifies the ACTIVE→SUSPENDED
 * transition round-trips through persistence, repeat suspension is rejected
 * after a real write, role enforcement holds, and a missing organization
 * resolves to {@code ORGANIZATION_NOT_FOUND} (application-layer.md §6).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class SuspendOrganizationPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private OrganizationRepository organizationRepository;

  private SuspendOrganizationHandler handler;

  @BeforeEach
  void setUp() {
    handler = new SuspendOrganizationHandler(organizationRepository, CLOCK);
  }

  @Test
  void suspendPersistsStatusTransition() {
    TenantId tenantId = seedOrganization("Suspend Org Test Alpha");

    SuspendOrganizationResult result = handler.handle(command(tenantId));

    assertEquals(tenantId, result.tenantId());
    assertEquals(Organization.Status.SUSPENDED, result.status());

    Organization reloaded = organizationRepository.findById(tenantId).orElseThrow();
    assertEquals(Organization.Status.SUSPENDED, reloaded.getStatus());
  }

  @Test
  void repeatedSuspendIsRejectedAfterPersistence() {
    TenantId tenantId = seedOrganization("Suspend Org Test Beta");
    handler.handle(command(tenantId));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId)));

    assertEquals(ApplicationError.ORGANIZATION_ALREADY_SUSPENDED, ex.getError());
    Organization reloaded = organizationRepository.findById(tenantId).orElseThrow();
    assertEquals(Organization.Status.SUSPENDED, reloaded.getStatus());
  }

  @Test
  void nonTenantAdminIsRejected() {
    TenantId tenantId = seedOrganization("Suspend Org Test Gamma");
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, tenantId)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    Organization reloaded = organizationRepository.findById(tenantId).orElseThrow();
    assertEquals(Organization.Status.ACTIVE, reloaded.getStatus());
  }

  @Test
  void missingOrganizationRaisesOrganizationNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(TenantId.generate())));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
  }

  private TenantId seedOrganization(String name) {
    Organization organization = new Organization(TenantId.generate(), name, NOW);
    return organizationRepository.save(organization).getId();
  }

  private SuspendOrganizationCommand command(TenantId tenantId) {
    return command(new Actor("admin-1", Actor.Role.TENANT_ADMIN), tenantId);
  }

  private SuspendOrganizationCommand command(Actor actor, TenantId tenantId) {
    return new SuspendOrganizationCommand(tenantId, actor);
  }
}
