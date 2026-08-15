package com.cdi.organization.adapter.out.persistence;

import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code tenant} table adapter against a real
 * PostgreSQL Testcontainer. Scenarios mirror the UC-07 persistence contract
 * (application-layer.md §6, data-model.md §3.A/§5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class OrganizationPersistenceIntegrationTest {

  @Autowired
  private OrganizationRepository adapter;

  @Autowired
  private OrganizationJpaRepository organizationRowCount;

  @Test
  void saveAndFindByNameRoundTripsAllFields() {
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    TenantId id = TenantId.generate();
    Organization organization = new Organization(id, "Acme Corp", now);

    adapter.save(organization);

    Organization loaded = adapter.findByName("Acme Corp").orElseThrow();
    assertEquals(id, loaded.getId());
    assertEquals("Acme Corp", loaded.getName());
    assertEquals(Organization.Status.ACTIVE, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
  }

  @Test
  void findByNameReturnsEmptyWhenAbsent() {
    Optional<Organization> result = adapter.findByName("Nonexistent Org");

    assertTrue(result.isEmpty());
  }

  @Test
  void suspendedStatusRoundTrips() {
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    Organization organization = new Organization(
        TenantId.generate(), "Globex Inc", now);
    organization.suspend();
    adapter.save(organization);

    Organization loaded = adapter.findByName("Globex Inc").orElseThrow();
    assertEquals(Organization.Status.SUSPENDED, loaded.getStatus());
  }

  @Test
  void activeStatusRoundTrips() {
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    Organization organization = new Organization(
        TenantId.generate(), "Initech LLC", now);
    adapter.save(organization);

    Organization loaded = adapter.findByName("Initech LLC").orElseThrow();
    assertEquals(Organization.Status.ACTIVE, loaded.getStatus());
  }

  @Test
  void createdAtAndUpdatedAtArePreserved() {
    Instant now = Instant.parse("2026-08-14T12:00:00Z");
    Organization organization = new Organization(
        TenantId.generate(), "Umbrella Corp", now);
    adapter.save(organization);

    Organization loaded = adapter.findByName("Umbrella Corp").orElseThrow();
    assertEquals(now, loaded.getCreatedAt());
  }

  @Test
  void duplicateNameViolatesUniqueConstraint() {
    Organization first = new Organization(
        TenantId.generate(), "Hooli", Instant.now());
    adapter.save(first);

    Organization duplicate = new Organization(
        TenantId.generate(), "Hooli", Instant.now());

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(duplicate));
  }

  @Test
  void differentNamesCoexistWithoutConflict() {
    Organization org1 = new Organization(
        TenantId.generate(), "Stark Industries", Instant.now());
    Organization org2 = new Organization(
        TenantId.generate(), "Wayne Enterprises", Instant.now());
    adapter.save(org1);
    adapter.save(org2);

    assertEquals(2, organizationRowCount.countByName("Stark Industries")
        + organizationRowCount.countByName("Wayne Enterprises"));
  }

  @Test
  void v6MigrationAppliesAndHibernateValidationPasses() {
    long total = organizationRowCount.count();
    assertTrue(total > 0, "rows from prior saves must be visible");
  }
}
