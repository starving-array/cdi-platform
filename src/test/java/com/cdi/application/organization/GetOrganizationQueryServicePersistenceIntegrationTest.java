package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetOrganizationQuery;
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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end UC-13 GetOrganization integration test: real JPA adapter
 * (Testcontainers PostgreSQL) wired into {@link GetOrganizationQueryService}.
 * Persists the Organization (tenant root) through the existing repository
 * adapter, then verifies the full aggregate read for both ENGINEER and
 * TENANT_ADMIN actors, status round-trip, missing-tenant and cross-tenant
 * {@code ORGANIZATION_NOT_FOUND}, and SYSTEM_WORKER role rejection
 * (application-layer.md §6 UC-13, data-model.md §3.A). Each test seeds its own
 * tenant id and a unique name to stay isolated inside the shared
 * (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetOrganizationQueryServicePersistenceIntegrationTest {

  @Autowired
  private OrganizationRepository organizationRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private GetOrganizationQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetOrganizationQueryService(organizationRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    Instant createdAt = Instant.parse("2026-08-14T12:00:00Z");
    TenantId tenantId = TenantId.generate();
    Organization seeded = new Organization(tenantId, "GetOrg Alpha", createdAt);
    organizationRepository.save(seeded);

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, engineer);

    assertEquals(seeded.getId(), organization.getId());
    assertEquals("GetOrg Alpha", organization.getName());
    assertEquals(Organization.Status.ACTIVE, organization.getStatus());
    assertEquals(createdAt, organization.getCreatedAt());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "GetOrg Beta", Instant.parse("2026-08-14T12:00:00Z")));

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, tenantAdmin);

    assertEquals("GetOrg Beta", organization.getName());
    assertEquals(Organization.Status.ACTIVE, organization.getStatus());
  }

  @Test
  void preservesSuspendedStatus() {
    TenantId tenantId = TenantId.generate();
    Organization suspended = Organization.restore(tenantId, "GetOrg Gamma",
        Organization.Status.SUSPENDED, Instant.parse("2026-08-14T12:00:00Z"));
    organizationRepository.save(suspended);

    Organization organization = service.handle(
        new GetOrganizationQuery(tenantId), tenantId, engineer);

    assertEquals(Organization.Status.SUSPENDED, organization.getStatus());
  }

  @Test
  void missingTenantRaisesOrganizationNotFound() {
    TenantId tenantId = TenantId.generate();

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetOrganizationQuery(TenantId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantQueryIsNotVisible() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "GetOrg Delta", Instant.parse("2026-08-14T12:00:00Z")));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetOrganizationQuery(tenantId), TenantId.generate(), tenantAdmin));

    assertEquals(ApplicationError.ORGANIZATION_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "GetOrg Epsilon", Instant.parse("2026-08-14T12:00:00Z")));

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetOrganizationQuery(tenantId), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }
}