package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.CreateOrganizationResult;
import com.cdi.application.port.in.CreateOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.event.TenantCreated;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application use case UC-07 CreateOrganization (application-layer.md §6,
 * bounded-contexts.md §1). Creates a new organization (tenant) in the
 * system or returns an existing one idempotently.
 *
 * <p>Coordinates the existing {@code Organization} domain aggregate and the
 * outbound persistence port only; it never re-implements domain rules.
 * Idempotency is owned here: a request whose organization {@code name}
 * natural key already exists resolves to the existing organization with
 * {@code created=false} instead of inserting a duplicate — no event is
 * emitted on reuse. The database {@code UNIQUE(name)} constraint is the
 * race-safe final guarantee for concurrent requests.
 *
 * <p>Role guard follows §9.1: UC-07's actor is {@code TENANT_ADMIN}. The
 * organization always starts {@code ACTIVE} per the {@code Organization}
 * constructor contract. The organization's {@code id} doubles as the
 * tenant identity (organization-repository-service-domain.md).
 *
 * <p>This is a synchronous admin write — no external ports are called and
 * nothing is enqueued. The only external side effect is the optional
 * {@link TenantCreated} event published through {@link DomainEventPublisher}
 * on new creation.
 */
public final class CreateOrganizationHandler {

  private final OrganizationRepository organizationRepository;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public CreateOrganizationHandler(
      OrganizationRepository organizationRepository,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.organizationRepository = organizationRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public CreateOrganizationHandler(
      OrganizationRepository organizationRepository,
      DomainEventPublisher eventPublisher) {
    this(organizationRepository, eventPublisher, Clock.systemUTC());
  }

  /**
   * Creates an organization, returning the result and whether it was newly
   * created.
   *
   * @return the {@link CreateOrganizationResult} carrying the organization
   *     (tenant) id and the created/reused flag
   * @throws ApplicationException for role violations; persistence/queue
   *     failures propagate via their own port exceptions
   */
  public CreateOrganizationResult handle(CreateOrganizationCommand command) {
    requireRole(command.actor());

    Optional<Organization> existing = organizationRepository.findByName(command.name());
    if (existing.isPresent()) {
      return new CreateOrganizationResult(existing.get().getId(), false);
    }

    Instant now = clock.instant();
    Organization organization = createOrganization(command.name(), now);
    organizationRepository.save(organization);
    eventPublisher.publish(TenantCreated.create(organization.getId(), organization.getName()));

    return new CreateOrganizationResult(organization.getId(), true);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  private Organization createOrganization(String name, Instant now) {
    return new Organization(TenantId.generate(), name, now);
  }
}
