package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.DeprecateServiceResult;
import com.cdi.application.port.in.DeprecateServiceCommand;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.Service;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use case P2 DeprecateService (application-layer.md §6,
 * organization-repository-service-domain.md) — the synchronous,
 * TENANT_ADMIN-only transition of a software service from
 * {@code ACTIVE} to {@code DEPRECATED}.
 *
 * <p><b>Frozen contract</b>: the actor must be {@code TENANT_ADMIN}
 * ({@code UNAUTHORIZED} otherwise). The service is resolved through the
 * tenant-scoped {@link ServiceRepository}; a missing service or
 * cross-tenant service resolves to {@code SERVICE_NOT_FOUND}
 * (data-model.md §2/§5). The domain transition {@link Service#deprecate()}
 * rejects an already-deprecated service with a {@link DomainException},
 * which is mapped to {@code SERVICE_ALREADY_DEPRECATED} (non-retryable,
 * 409) following the P2 {@code REPOSITORY_ALREADY_ARCHIVED} pattern — the
 * deprecation is one-shot and non-idempotent (application-layer.md §10).
 *
 * <p><b>Semantics</b>: no domain event is published and no
 * external system calls are made — the status transition is persisted through the
 * existing {@link ServiceRepository} {@code save()} path only.
 */
public final class DeprecateServiceHandler {

  private final ServiceRepository serviceRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public DeprecateServiceHandler(
      ServiceRepository serviceRepository,
      Clock clock) {
    this.serviceRepository =
        Objects.requireNonNull(serviceRepository, "ServiceRepository");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Creates the handler using the system clock.
   */
  public DeprecateServiceHandler(ServiceRepository serviceRepository) {
    this(serviceRepository, Clock.systemUTC());
  }

  /**
   * Deprecates the service and persists the transition.
   *
   * @return the {@link DeprecateServiceResult} carrying the service id and
   *     the resulting {@code DEPRECATED} status
   * @throws ApplicationException for role violations, a missing service,
   *     and the already-deprecated rejection
   */
  public DeprecateServiceResult handle(DeprecateServiceCommand command) {
    requireRole(command.actor());

    Service service = resolveService(command.tenantId(), command.serviceId());
    deprecate(service);

    Service saved = serviceRepository.save(service);
    return new DeprecateServiceResult(saved.getId(), saved.getStatus());
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  /**
   * Resolves the tenant-scoped service. The lookup is strictly scoped by
   * both {@code tenantId} and {@code serviceId}, so a missing row or a
   * foreign tenant id resolves to {@code SERVICE_NOT_FOUND}.
   */
  private Service resolveService(TenantId tenantId, ServiceId serviceId) {
    Optional<Service> existing =
        serviceRepository.findByTenantIdAndId(tenantId, serviceId);
    return existing.orElseThrow(
        () -> new ApplicationException(ApplicationError.SERVICE_NOT_FOUND));
  }

  /**
   * Applies the ACTIVE→DEPRECATED domain transition. The domain guard throws a
   * {@link DomainException} for an already-deprecated service, translated
   * here to the typed {@code SERVICE_ALREADY_DEPRECATED} application error
   * (never a generic runtime exception).
   */
  private void deprecate(Service service) {
    try {
      service.deprecate();
    } catch (DomainException e) {
      throw new ApplicationException(ApplicationError.SERVICE_ALREADY_DEPRECATED,
          Map.of("reason", "already-deprecated", "detail", e.getMessage()));
    }
  }
}