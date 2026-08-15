package com.cdi.systemcontext.adapter.out.persistence;

import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code service} table adapter against a real
 * PostgreSQL Testcontainer. Scenarios mirror the UC-09 persistence contract
 * (application-layer.md §6, data-model.md §3.A/§5, V8 migration).
 *
 * <p>Because V8 declares {@code FK(tenant_id) REFERENCES tenant(id)}, every
 * service row requires a pre-existing tenant row. A real {@code Organization}
 * is persisted via the existing {@link OrganizationRepository} adapter before
 * each test (no direct manipulation of the {@code tenant} table).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ServicePersistenceIntegrationTest {

  @Autowired
  private ServiceRepository adapter;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private ServiceJpaRepository serviceRowCount;

  @Test
  void saveAndFindByNaturalKeyRoundTripsAllFields() {
    TenantId tenantId = createTenant();
    Instant now = Instant.parse("2026-08-15T12:00:00Z");
    Service service = new Service(
        ServiceId.generate(), tenantId, "payment-service", CriticalityTier.TIER_0,
        "team-payments", now);

    adapter.save(service);

    Service loaded = adapter.findByTenantIdAndName(
        tenantId, "payment-service").orElseThrow();
    assertEquals(service.getId(), loaded.getId());
    assertEquals(tenantId, loaded.getTenantId());
    assertEquals("payment-service", loaded.getName());
    assertEquals(CriticalityTier.TIER_0, loaded.getCriticality());
    assertEquals("team-payments", loaded.getOwner());
    assertEquals(Service.Status.ACTIVE, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
  }

  @Test
  void findByNaturalKeyReturnsEmptyWhenAbsent() {
    TenantId tenantId = createTenant();

    Optional<Service> result = adapter.findByTenantIdAndName(
        tenantId, "no-such-service");

    assertTrue(result.isEmpty());
  }

  @Test
  void duplicateNameViolatesUniqueConstraint() {
    TenantId tenantId = createTenant();
    Service first = new Service(
        ServiceId.generate(), tenantId, "billing-service", CriticalityTier.TIER_1,
        "team-billing", Instant.now());
    adapter.save(first);

    Service duplicate = new Service(
        ServiceId.generate(), tenantId, "billing-service", CriticalityTier.TIER_2,
        "team-other", Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(duplicate));
  }

  @Test
  void tenantIsolationPreventsCrossTenantLookup() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();
    Service service = new Service(
        ServiceId.generate(), tenantA, "shared-name", CriticalityTier.TIER_0,
        "team-a", Instant.now());
    adapter.save(service);

    Optional<Service> onTenantB = adapter.findByTenantIdAndName(
        tenantB, "shared-name");
    assertTrue(onTenantB.isEmpty());

    Service onTenantA = adapter.findByTenantIdAndName(
        tenantA, "shared-name").orElseThrow();
    assertEquals(service.getId(), onTenantA.getId());
  }

  @Test
  void sameNameAllowedAcrossDifferentTenants() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();

    adapter.save(new Service(
        ServiceId.generate(), tenantA, "shared-name", CriticalityTier.TIER_0,
        "team-a", Instant.now()));
    adapter.save(new Service(
        ServiceId.generate(), tenantB, "shared-name", CriticalityTier.TIER_1,
        "team-b", Instant.now()));

    Service a = adapter.findByTenantIdAndName(
        tenantA, "shared-name").orElseThrow();
    Service b = adapter.findByTenantIdAndName(
        tenantB, "shared-name").orElseThrow();
    assertEquals("team-a", a.getOwner());
    assertEquals("team-b", b.getOwner());
  }

  @Test
  void nonExistentTenantRejectedByForeignKey() {
    TenantId orphanTenant = TenantId.generate();
    Service service = new Service(
        ServiceId.generate(), orphanTenant, "orphan-service", CriticalityTier.TIER_3,
        "team-orphan", Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(service));
  }

  @Test
  void v8MigrationAppliesAndHibernateValidationPasses() {
    // Hibernate ddl-auto: validate runs at context startup. If the
    // ServiceEntity fields did not exactly match the V8 columns, the
    // @SpringBootTest context would fail to load and this test (and the rest
    // of the suite sharing the context) would not execute. Reaching this
    // assertion with a counted row confirms the migration applied and the
    // entity validates.
    TenantId tenantId = createTenant();
    adapter.save(new Service(
        ServiceId.generate(), tenantId, "validate-service", CriticalityTier.TIER_0,
        "validate-team", Instant.now()));

    assertTrue(serviceRowCount.countByTenantId(tenantId.value()) >= 1);
  }

  @Test
  void deprecatedStateRoundTripsViaRestore() {
    TenantId tenantId = createTenant();
    Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");
    Service service = Service.restore(
        ServiceId.generate(), tenantId, "legacy-service", CriticalityTier.TIER_2,
        "team-legacy", Service.Status.DEPRECATED, createdAt);

    adapter.save(service);

    Service loaded = adapter.findByTenantIdAndName(
        tenantId, "legacy-service").orElseThrow();
    assertEquals(Service.Status.DEPRECATED, loaded.getStatus());
    assertEquals(service.getId(), loaded.getId());
    assertEquals(CriticalityTier.TIER_2, loaded.getCriticality());
    assertEquals("team-legacy", loaded.getOwner());
    assertEquals(createdAt, loaded.getCreatedAt());
    assertNull(loaded.getRepositoryId());
  }

  /**
   * Persists a real tenant row so the service FK is satisfied. Uses a unique
   * organization name per call to avoid collisions with rows left by other
   * tests (the existing org-integration tests do not clean up between tests).
   */
  private TenantId createTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "service-test-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }
}
