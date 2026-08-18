package com.cdi.application.organization;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.SuspendOrganizationResult;
import com.cdi.application.port.in.SuspendOrganizationCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use case P2 SuspendOrganization (application-layer.md §6,
 * organization-repository-service-domain.md) — the synchronous,
 * TENANT_ADMIN-only transition of an organization (tenant root) from
 * {@code ACTIVE} to {@code SUSPENDED}.
 *
 * <p><b>Frozen contract (D1-D4)</b>: the actor must be {@code TENANT_ADMIN}
 * ({@code UNAUTHORIZED} otherwise). The organization is resolved through the
 * tenant-root-scoped {@link OrganizationRepository}; a missing organization
 * resolves to {@code ORGANIZATION_NOT_FOUND} (a cross-tenant id is simply a
 * missing organization — the id <em>is</em> the tenant identity,
 * data-model.md §3.A). The domain transition {@link Organization#suspend()}
 * rejects an already-suspended organization with a {@link DomainException},
 * which is mapped to {@code ORGANIZATION_ALREADY_SUSPENDED} (non-retryable,
 * 409) following the UC-06 {@code DECISION_ALREADY_OVERRIDDEN} pattern — the
 * suspend is one-shot and non-idempotent (application-layer.md §10).
 *
 * <p><b>Semantics (D5/D6)</b>: no domain event is published and no
 * {@code updated_at} promotion or timestamp behavior is added — the status
 * transition is persisted through the existing {@link OrganizationRepository}
 * {@code save()} path only. No external ports are called and nothing is
 * enqueued.
 */
public final class SuspendOrganizationHandler {

  private final OrganizationRepository organizationRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public SuspendOrganizationHandler(
      OrganizationRepository organizationRepository,
      Clock clock) {
    this.organizationRepository =
        Objects.requireNonNull(organizationRepository, "OrganizationRepository");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Creates the handler using the system clock.
   */
  public SuspendOrganizationHandler(OrganizationRepository organizationRepository) {
    this(organizationRepository, Clock.systemUTC());
  }

  /**
   * Suspends the organization (tenant) and persists the transition.
   *
   * @return the {@link SuspendOrganizationResult} carrying the tenant id and
   *     the resulting {@code SUSPENDED} status
   * @throws ApplicationException for role violations, a missing organization,
   *     and the already-suspended rejection
   */
  public SuspendOrganizationResult handle(SuspendOrganizationCommand command) {
    requireRole(command.actor());

    Organization organization = resolveOrganization(command.tenantId());
    suspend(organization);

    Organization saved = organizationRepository.save(organization);
    return new SuspendOrganizationResult(saved.getId(), saved.getStatus());
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  /**
   * Resolves the tenant-root organization. The lookup is scoped by the
   * organization's own id, which <em>is</em> the tenant identity, so a missing
   * row — or a foreign tenant id — is {@code ORGANIZATION_NOT_FOUND}
   * (GetOrganization pattern, application-layer.md §6).
   */
  private Organization resolveOrganization(TenantId tenantId) {
    Optional<Organization> existing = organizationRepository.findById(tenantId);
    return existing.orElseThrow(
        () -> new ApplicationException(ApplicationError.ORGANIZATION_NOT_FOUND));
  }

  /**
   * Applies the ACTIVE→SUSPENDED domain transition. The domain guard throws a
   * {@link DomainException} for an already-suspended organization, translated
   * here to the typed {@code ORGANIZATION_ALREADY_SUSPENDED} application error
   * (never a generic runtime exception).
   */
  private void suspend(Organization organization) {
    try {
      organization.suspend();
    } catch (DomainException e) {
      throw new ApplicationException(ApplicationError.ORGANIZATION_ALREADY_SUSPENDED,
          Map.of("reason", "already-suspended", "detail", e.getMessage()));
    }
  }
}
