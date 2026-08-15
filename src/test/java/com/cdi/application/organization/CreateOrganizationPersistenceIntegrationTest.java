package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.CreateOrganizationResult;
import com.cdi.application.port.in.CreateOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.organization.adapter.out.persistence.OrganizationJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-07 integration test: real {@code OrganizationRepository} JPA
 * adapter (Testcontainers PostgreSQL) wired into {@code CreateOrganizationHandler},
 * with a fake only at the event-publisher boundary. Verifies the full persist
 * path, cross-process idempotent reuse, and that duplicate names never create
 * a second organization row (application-layer.md §6/§10).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class CreateOrganizationPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private OrganizationJpaRepository organizationRowCount;

  private RecordingEventPublisher eventPublisher;
  private CreateOrganizationHandler handler;

  @BeforeEach
  void setUp() {
    eventPublisher = new RecordingEventPublisher();
    handler = new CreateOrganizationHandler(
        organizationRepository, eventPublisher, CLOCK);
  }

  @Test
  void creationPersistsOrganizationAndIsIdempotent() {
    CreateOrganizationCommand command = command("Pied Piper");

    CreateOrganizationResult first = handler.handle(command);

    assertTrue(first.created());
    assertEquals(1, eventPublisher.published);

    Organization saved = organizationRepository.findByName("Pied Piper").orElseThrow();
    assertEquals(first.organizationId(), saved.getId());
    assertEquals(Organization.Status.ACTIVE, saved.getStatus());
    assertEquals(NOW, saved.getCreatedAt());

    CreateOrganizationResult replay = handler.handle(command);

    assertFalse(replay.created());
    assertEquals(first.organizationId(), replay.organizationId());
    assertEquals(1, eventPublisher.published);
    assertEquals(1, organizationRowCount.countByName("Pied Piper"));
  }

  @Test
  void differentOrganizationsAreDistinct() {
    CreateOrganizationResult first = handler.handle(command("Riviera Industries"));
    CreateOrganizationResult second = handler.handle(command("Soylent Corp"));

    assertTrue(first.created());
    assertTrue(second.created());
    assertEquals(2, eventPublisher.published);
    assertEquals(1, organizationRowCount.countByName("Riviera Industries"));
    assertEquals(1, organizationRowCount.countByName("Soylent Corp"));
  }

  private CreateOrganizationCommand command(String name) {
    return new CreateOrganizationCommand(
        name, new Actor("admin-1", Actor.Role.TENANT_ADMIN),
        new IdempotencyKey("header-key-1"));
  }

  private static class RecordingEventPublisher implements DomainEventPublisher {
    int published = 0;

    @Override
    public void publish(DomainEvent event) {
      published++;
    }
  }
}
