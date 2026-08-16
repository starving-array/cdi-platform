package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetOrganizationQuery;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for UC-13 GetOrganization. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. Verifies the full aggregate read for both ENGINEER and
 * TENANT_ADMIN actors, missing-tenant and cross-tenant
 * {@code ORGANIZATION_NOT_FOUND}, and SYSTEM_WORKER role rejection.
 */
class GetOrganizationQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakeOrganizationRepository organizationRepository;
  private GetOrganizationQueryService service;

  @BeforeEach
  void setUp() {
    organizationRepository = new FakeOrganizationRepository();
    service = new GetOrganizationQueryService(organizationRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    Organization seeded = organizationRepository.seed(tenantId, "Acme Corp",
        Instant.parse("2026-08-14T12:00:00Z"));

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, engineer);

    assertEquals(seeded.getId(), organization.getId());
    assertEquals("Acme Corp", organization.getName());
    assertEquals(Organization.Status.ACTIVE, organization.getStatus());
    assertEquals(seeded.getCreatedAt(), organization.getCreatedAt());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    organizationRepository.seed(tenantId, "Acme Corp", Instant.parse("2026-08-14T12:00:00Z"));

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, tenantAdmin);

    assertEquals("Acme Corp", organization.getName());
    assertEquals(Organization.Status.ACTIVE, organization.getStatus());
  }

  @Test
  void preservesSuspendedStatus() {
    Organization seeded = organizationRepository.seed(tenantId, "Acme Corp",
        Instant.parse("2026-08-14T12:00:00Z"));
    seeded.suspend();

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, engineer);

    assertEquals(Organization.Status.SUSPENDED, organization.getStatus());
  }

  @Test
  void missingTenantRaisesOrganizationNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetOrganizationQuery(TenantId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantQueryIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    organizationRepository.seed(tenantB, "Acme Corp", Instant.parse("2026-08-14T12:00:00Z"));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetOrganizationQuery(tenantB), tenantId, engineer));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    organizationRepository.seed(tenantId, "Acme Corp", Instant.parse("2026-08-14T12:00:00Z"));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetOrganizationQuery(tenantId), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private static class FakeOrganizationRepository implements OrganizationRepository {
    final Map<UUID, Organization> byId = new HashMap<>();

    Organization seed(TenantId tenantId, String name, Instant createdAt) {
      Organization organization = new Organization(tenantId, name, createdAt);
      byId.put(tenantId.value(), organization);
      return organization;
    }

    @Override
    public Optional<Organization> findByName(String name) {
      return byId.values().stream()
          .filter(o -> o.getName().equals(name))
          .findFirst();
    }

    @Override
    public Optional<Organization> findById(TenantId tenantId) {
      return Optional.ofNullable(byId.get(tenantId.value()));
    }

    @Override
    public Organization save(Organization organization) {
      byId.put(organization.getId().value(), organization);
      return organization;
    }
  }
}