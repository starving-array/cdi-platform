package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.SuspendOrganizationResult;
import com.cdi.application.port.in.SuspendOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
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
 * Unit tests for the P2 SuspendOrganization capability. Uses fake ports only at
 * the application boundary; no Testcontainers, no Spring context, no
 * persistence is started.
 */
class SuspendOrganizationHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private FakeOrganizationRepository organizationRepository;
  private SuspendOrganizationHandler handler;

  @BeforeEach
  void setUp() {
    organizationRepository = new FakeOrganizationRepository();
    handler = new SuspendOrganizationHandler(organizationRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminSuspendTransitionsActiveToSuspended() {
    Organization organization = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put(organization.getId().value(), organization);

    SuspendOrganizationResult result = handler.handle(command(organization.getId()));

    assertEquals(organization.getId(), result.tenantId());
    assertEquals(Organization.Status.SUSPENDED, result.status());
    assertEquals(1, organizationRepository.savedOrganizations.size());
    assertEquals(Organization.Status.SUSPENDED,
        organizationRepository.savedOrganizations.get(0).getStatus());
  }

  @Test
  void unauthorizedActorRejected() {
    Organization organization = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put(organization.getId().value(), organization);

    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(engineer, organization.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
  }

  @Test
  void systemWorkerRoleRejected() {
    Organization organization = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put(organization.getId().value(), organization);

    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(worker, organization.getId())));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
  }

  @Test
  void missingOrganizationRaisesOrganizationNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(TenantId.generate())));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
  }

  @Test
  void alreadySuspendedOrganizationRaisesAlreadySuspended() {
    TenantId tenantId = TenantId.generate();
    Organization organization = Organization.restore(
        tenantId, "Acme Corp", Organization.Status.SUSPENDED, NOW.minusSeconds(3600));
    organizationRepository.organizations.put(tenantId.value(), organization);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(command(tenantId)));

    assertEquals(ApplicationError.ORGANIZATION_ALREADY_SUSPENDED, ex.getError());
    assertEquals("already-suspended", ex.getDetails().get("reason"));
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
  }

  @Test
  void persistenceFailurePropagates() {
    Organization organization = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put(organization.getId().value(), organization);
    organizationRepository.throwOnSave = true;

    assertThrows(RuntimeException.class, () -> handler.handle(command(organization.getId())));
  }

  private SuspendOrganizationCommand command(TenantId tenantId) {
    return command(tenantAdmin, tenantId);
  }

  private SuspendOrganizationCommand command(Actor actor, TenantId tenantId) {
    return new SuspendOrganizationCommand(tenantId, actor);
  }

  private static class FakeOrganizationRepository implements OrganizationRepository {
    final Map<java.util.UUID, Organization> organizations = new HashMap<>();
    final java.util.List<Organization> savedOrganizations = new java.util.ArrayList<>();
    boolean throwOnSave = false;

    @Override
    public Optional<Organization> findByName(String name) {
      return organizations.values().stream()
          .filter(o -> o.getName().equals(name))
          .findFirst();
    }

    @Override
    public Optional<Organization> findById(TenantId tenantId) {
      return Optional.ofNullable(organizations.get(tenantId.value()));
    }

    @Override
    public Organization save(Organization organization) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedOrganizations.add(organization);
      organizations.put(organization.getId().value(), organization);
      return organization;
    }
  }
}
