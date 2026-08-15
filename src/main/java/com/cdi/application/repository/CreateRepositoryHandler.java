package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreateRepositoryResult;
import com.cdi.application.port.in.CreateRepositoryCommand;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.repository.domain.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application use case UC-08 CreateRepository (application-layer.md §6,
 * bounded-contexts.md §2). Creates a new source-control repository under an
 * existing tenant or returns an existing one idempotently.
 *
 * <p>Coordinates the existing {@code Repository} domain aggregate and the
 * outbound persistence port only; it never re-implements domain rules.
 * Idempotency is owned here: a request whose natural key
 * {@code (tenantId, providerType, externalId)} already exists resolves to the
 * existing repository with {@code created=false} instead of inserting a
 * duplicate — no save, no event is emitted on reuse. The database
 * {@code UNIQUE (tenant_id, provider_type, external_id)} constraint (V7) is the
 * race-safe final guarantee for concurrent requests; a concurrent duplicate
 * surfaces as a persistence-layer {@code DataIntegrityViolationException} and
 * is not converted into a fake successful creation.
 *
 * <p>Role guard follows §9.1: UC-08's actor is {@code TENANT_ADMIN}. The
 * repository always starts {@code ACTIVE} per the {@code Repository}
 * constructor contract (organization-repository-service-domain.md).
 *
 * <p>This is a synchronous admin write — no external ports are called, nothing
 * is enqueued, and no domain event is emitted (UC-08 contract: no canonical
 * {@code RepositoryCreated} event exists; application-layer.md §12 "publish
 * only what has a consumer or audit need").
 */
public final class CreateRepositoryHandler {

  private final RepositoryRepository repositoryRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public CreateRepositoryHandler(RepositoryRepository repositoryRepository, Clock clock) {
    this.repositoryRepository = repositoryRepository;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public CreateRepositoryHandler(RepositoryRepository repositoryRepository) {
    this(repositoryRepository, Clock.systemUTC());
  }

  /**
   * Creates a repository, returning the result and whether it was newly
   * created.
   *
   * @return the {@link CreateRepositoryResult} carrying the repository id and
   *     the created/reused flag
   * @throws ApplicationException for role violations; persistence failures
   *     (including concurrent {@code UNIQUE} violations) propagate via their
   *     own port exceptions
   */
  public CreateRepositoryResult handle(CreateRepositoryCommand command) {
    requireRole(command.actor());

    Optional<Repository> existing = repositoryRepository.findByTenantIdProviderTypeExternalId(
        command.tenantId(), command.providerType(), command.externalId());
    if (existing.isPresent()) {
      return new CreateRepositoryResult(existing.get().getId(), false);
    }

    Instant now = clock.instant();
    Repository repository = createRepository(command, now);
    repositoryRepository.save(repository);

    return new CreateRepositoryResult(repository.getId(), true);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  private Repository createRepository(CreateRepositoryCommand command, Instant now) {
    return new Repository(
        RepositoryId.generate(),
        command.tenantId(),
        command.providerType(),
        command.externalId(),
        command.name(),
        command.url(),
        command.defaultBranch(),
        now);
  }
}
