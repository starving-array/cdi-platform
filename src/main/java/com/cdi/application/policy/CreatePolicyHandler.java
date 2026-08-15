package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreatePolicyResult;
import com.cdi.application.port.in.CreatePolicyCommand;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application use case UC-10 CreatePolicy (application-layer.md §6,
 * bounded-contexts.md §7). Creates a new ACTIVE policy for a tenant or returns
 * the existing active one idempotently.
 *
 * <p>Coordinates the existing {@code Policy} domain aggregate and the
 * outbound persistence port only; it never re-implements domain rules.
 *
 * <p><b>Idempotency (resolved audit Decision 1)</b>: exactly one ACTIVE policy
 * per tenant. A request whose tenant already has an active policy resolves to
 * the existing policy with {@code created=false} instead of inserting a
 * duplicate — no save. The PostgreSQL
 * {@code UNIQUE (tenant_id) WHERE status = 'ACTIVE'} partial unique constraint
 * (V9) is the race-safe final guarantee; a concurrent duplicate insert
 * surfaces as a persistence-layer {@code DataIntegrityViolationException} and
 * is not converted into a fake successful creation (UC-08/UC-09 precedent).
 *
 * <p>Role guard follows §9.1: UC-10's actor is {@code TENANT_ADMIN}. The
 * policy always starts {@code ACTIVE} per the resolved audit contract.
 *
 * <p>This is a synchronous admin write — no external ports are called, nothing
 * is enqueued, and no domain event is emitted (resolved audit Decision 3: no
 * canonical {@code PolicyCreated} event exists; application-layer.md §12
 * "publish only what has a consumer or audit need" — UC-08/UC-09 precedent).
 */
public final class CreatePolicyHandler {

  private final PolicyRepository policyRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public CreatePolicyHandler(PolicyRepository policyRepository, Clock clock) {
    this.policyRepository = policyRepository;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public CreatePolicyHandler(PolicyRepository policyRepository) {
    this(policyRepository, Clock.systemUTC());
  }

  /**
   * Creates a policy, returning the result and whether it was newly created.
   *
   * @return the {@link CreatePolicyResult} carrying the policy id and the
   *     created/reused flag
   * @throws ApplicationException for role violations; persistence failures
   *     (including concurrent unique-violation) propagate via their own port
   *     exceptions
   */
  public CreatePolicyResult handle(CreatePolicyCommand command) {
    requireRole(command.actor());

    Optional<Policy> existing = policyRepository.findActiveByTenant(command.tenantId());
    if (existing.isPresent()) {
      return new CreatePolicyResult(existing.get().getId(), false);
    }

    Instant now = clock.instant();
    Policy policy = createPolicy(command, now);
    policyRepository.save(policy);

    return new CreatePolicyResult(policy.getId(), true);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  private Policy createPolicy(CreatePolicyCommand command, Instant now) {
    return new Policy(
        PolicyId.generate(),
        command.tenantId(),
        command.name(),
        command.description(),
        PolicyStatus.ACTIVE,
        command.version(),
        command.rules(),
        now);
  }
}
