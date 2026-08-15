package com.cdi.application.systemcontext;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreateServiceResult;
import com.cdi.application.port.in.CreateServiceCommand;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.systemcontext.domain.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application use case UC-09 CreateService (application-layer.md §6,
 * bounded-contexts.md §3). Creates a new system-context service under an
 * existing tenant or returns an existing one idempotently.
 *
 * <p>Coordinates the existing {@code Service} domain aggregate and the
 * outbound persistence port only; it never re-implements domain rules.
 * Idempotency is owned here: a request whose natural key
 * {@code (tenantId, name)} already exists resolves to the existing service
 * with {@code created=false} instead of inserting a duplicate — no save, no
 * event is emitted on reuse. The database
 * {@code UNIQUE (tenant_id, name)} constraint (V8) is the race-safe final
 * guarantee for concurrent requests; a concurrent duplicate surfaces as a
 * persistence-layer {@code DataIntegrityViolationException} and is not
 * converted into a fake successful creation.
 *
 * <p>Role guard follows §9.1: UC-09's actor is {@code TENANT_ADMIN}. The
 * service always starts {@code ACTIVE} per the {@code Service} constructor
 * contract (organization-repository-service-domain.md).
 *
 * <p>This is a synchronous admin write — no external ports are called, nothing
 * is enqueued, and no domain event is emitted (UC-09 contract: no canonical
 * {@code ServiceCreated} event exists; application-layer.md §12 "publish
 * only what has a consumer or audit need").
 */
public final class CreateServiceHandler {

  private final ServiceRepository serviceRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public CreateServiceHandler(ServiceRepository serviceRepository, Clock clock) {
    this.serviceRepository = serviceRepository;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public CreateServiceHandler(ServiceRepository serviceRepository) {
    this(serviceRepository, Clock.systemUTC());
  }

  /**
   * Creates a service, returning the result and whether it was newly
   * created.
   *
   * @return the {@link CreateServiceResult} carrying the service id and
   *     the created/reused flag
   * @throws ApplicationException for role violations; persistence failures
   *     (including concurrent {@code UNIQUE} violations) propagate via their
   *     own port exceptions
   */
  public CreateServiceResult handle(CreateServiceCommand command) {
    requireRole(command.actor());

    Optional<Service> existing = serviceRepository.findByTenantIdAndName(
        command.tenantId(), command.name());
    if (existing.isPresent()) {
      return new CreateServiceResult(existing.get().getId(), false);
    }

    Instant now = clock.instant();
    Service service = createService(command, now);
    serviceRepository.save(service);

    return new CreateServiceResult(service.getId(), true);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  private Service createService(CreateServiceCommand command, Instant now) {
    return new Service(
        ServiceId.generate(),
        command.tenantId(),
        command.name(),
        command.criticalityTier(),
        command.owner(),
        now);
  }
}
