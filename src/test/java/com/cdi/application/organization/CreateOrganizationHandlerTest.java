package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.CreateOrganizationResult;
import com.cdi.application.port.in.CreateOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.event.TenantCreated;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-07 CreateOrganization. Uses fake ports only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started.
 */
class CreateOrganizationHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private FakeOrganizationRepository organizationRepository;
  private FakeDomainEventPublisher eventPublisher;
  private CreateOrganizationHandler handler;

  @BeforeEach
  void setUp() {
    organizationRepository = new FakeOrganizationRepository();
    eventPublisher = new FakeDomainEventPublisher();
    handler = new CreateOrganizationHandler(
        organizationRepository, eventPublisher, CLOCK);
  }

  @Test
  void successfulTenantAdminCreation() {
    CreateOrganizationResult result = handler.handle(command("Acme Corp"));

    assertTrue(result.created());
    assertNotNull(result.organizationId());
    assertEquals(1, organizationRepository.savedOrganizations.size());
  }

  @Test
  void createdOrganizationStartsActiveWithCorrectTimestamp() {
    handler.handle(command("Acme Corp"));

    Organization saved = organizationRepository.savedOrganizations.get(0);
    assertEquals(Organization.Status.ACTIVE, saved.getStatus());
    assertEquals(NOW, saved.getCreatedAt());
    assertEquals("Acme Corp", saved.getName());
  }

  @Test
  void organizationIdIsGeneratedTenantId() {
    CreateOrganizationResult result = handler.handle(command("Acme Corp"));

    Organization saved = organizationRepository.savedOrganizations.get(0);
    assertEquals(saved.getId(), result.organizationId());
    assertNotNull(result.organizationId().value());
  }

  @Test
  void unauthorizedActorRejected() {
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    CreateOrganizationCommand engineerCommand = command(engineer, "Acme Corp");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(engineerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void systemWorkerRoleRejected() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    CreateOrganizationCommand workerCommand = command(worker, "Acme Corp");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(workerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void blankNameRejectedByCommandContract() {
    assertThrows(DomainException.class, () -> command("  "));
    assertThrows(DomainException.class, () -> new CreateOrganizationCommand(
        null, tenantAdmin, new IdempotencyKey("key-1")));
  }

  @Test
  void duplicateOrganizationIsReusedIdempotently() {
    Organization existing = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put("Acme Corp", existing);

    CreateOrganizationResult result = handler.handle(command("Acme Corp"));

    assertFalse(result.created());
    assertEquals(existing.getId(), result.organizationId());
    assertTrue(organizationRepository.savedOrganizations.isEmpty());
  }

  @Test
  void idempotentRepeatedInvocationEmitsNoDuplicateEvent() {
    handler.handle(command("Acme Corp"));
    assertEquals(1, eventPublisher.events.size());

    handler.handle(command("Acme Corp"));
    assertEquals(1, eventPublisher.events.size());
  }

  @Test
  void duplicateReuseDoesNotEmitEvent() {
    Organization existing = new Organization(
        TenantId.generate(), "Acme Corp", NOW.minusSeconds(3600));
    organizationRepository.organizations.put("Acme Corp", existing);

    handler.handle(command("Acme Corp"));

    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void newCreationEmitsTenantCreatedEvent() {
    CreateOrganizationResult result = handler.handle(command("Acme Corp"));

    assertEquals(1, eventPublisher.events.size());
    TenantCreated event = (TenantCreated) eventPublisher.events.get(0);
    assertEquals(result.organizationId(), event.tenantId());
    assertEquals("Acme Corp", event.name());
  }

  @Test
  void differentNamesCreateDifferentOrganizations() {
    handler.handle(command("Acme Corp"));
    handler.handle(command("Globex Inc"));

    assertEquals(2, organizationRepository.savedOrganizations.size());
    assertEquals(2, eventPublisher.events.size());
  }

  @Test
  void nameIsTrimmedBeforePersistence() {
    handler.handle(command("  Acme Corp  "));

    assertEquals("Acme Corp", organizationRepository.savedOrganizations.get(0).getName());
  }

  @Test
  void persistenceFailurePropagates() {
    organizationRepository.throwOnSave = true;

    assertThrows(RuntimeException.class, () -> handler.handle(command("Acme Corp")));
  }

  private CreateOrganizationCommand command(String name) {
    return command(tenantAdmin, name);
  }

  private CreateOrganizationCommand command(Actor actor, String name) {
    return new CreateOrganizationCommand(name, actor, new IdempotencyKey("header-key-1"));
  }

  private static class FakeOrganizationRepository implements OrganizationRepository {
    final Map<String, Organization> organizations = new HashMap<>();
    final List<Organization> savedOrganizations = new ArrayList<>();
    boolean throwOnSave = false;

    @Override
    public Optional<Organization> findByName(String name) {
      return Optional.ofNullable(organizations.get(name));
    }

    @Override
    public Organization save(Organization organization) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedOrganizations.add(organization);
      organizations.put(organization.getName(), organization);
      return organization;
    }
  }

  private static class FakeDomainEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
    }
  }
}
