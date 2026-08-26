package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.ArchiveRepositoryResult;
import com.cdi.application.port.in.ArchiveRepositoryCommand;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use case P2 ArchiveRepository (application-layer.md §6,
 * organization-repository-service-domain.md) — the synchronous,
 * TENANT_ADMIN-only transition of a source-control repository from
 * {@code ACTIVE} to {@code ARCHIVED}.
 *
 * <p><b>Frozen contract</b>: the actor must be {@code TENANT_ADMIN}
 * ({@code UNAUTHORIZED} otherwise). The repository is resolved through the
 * tenant-scoped {@link RepositoryRepository}; a missing repository or
 * cross-tenant repository resolves to {@code REPOSITORY_NOT_FOUND}
 * (data-model.md §2/§5). The domain transition {@link Repository#archive()}
 * rejects an already-archived repository with a {@link DomainException},
 * which is mapped to {@code REPOSITORY_ALREADY_ARCHIVED} (non-retryable,
 * 409) following the P2 {@code ORGANIZATION_ALREADY_SUSPENDED} pattern — the
 * archive is one-shot and non-idempotent (application-layer.md §10).
 *
 * <p><b>Semantics</b>: no domain event is published and no
 * external SCM calls are made — the status transition is persisted through the
 * existing {@link RepositoryRepository} {@code save()} path only.
 */
public final class ArchiveRepositoryHandler {

  private final RepositoryRepository repositoryRepository;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public ArchiveRepositoryHandler(
      RepositoryRepository repositoryRepository,
      Clock clock) {
    this.repositoryRepository =
        Objects.requireNonNull(repositoryRepository, "RepositoryRepository");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Creates the handler using the system clock.
   */
  public ArchiveRepositoryHandler(RepositoryRepository repositoryRepository) {
    this(repositoryRepository, Clock.systemUTC());
  }

  /**
   * Archives the repository and persists the transition.
   *
   * @return the {@link ArchiveRepositoryResult} carrying the repository id and
   *     the resulting {@code ARCHIVED} status
   * @throws ApplicationException for role violations, a missing repository,
   *     and the already-archived rejection
   */
  public ArchiveRepositoryResult handle(ArchiveRepositoryCommand command) {
    requireRole(command.actor());

    Repository repository = resolveRepository(command.tenantId(), command.repositoryId());
    archive(repository);

    Repository saved = repositoryRepository.save(repository);
    return new ArchiveRepositoryResult(saved.getId(), saved.getStatus());
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  /**
   * Resolves the tenant-scoped repository. The lookup is strictly scoped by
   * both {@code tenantId} and {@code repositoryId}, so a missing row or a
   * foreign tenant id resolves to {@code REPOSITORY_NOT_FOUND}.
   */
  private Repository resolveRepository(TenantId tenantId, RepositoryId repositoryId) {
    Optional<Repository> existing =
        repositoryRepository.findByTenantIdAndId(tenantId, repositoryId);
    return existing.orElseThrow(
        () -> new ApplicationException(ApplicationError.REPOSITORY_NOT_FOUND));
  }

  /**
   * Applies the ACTIVE→ARCHIVED domain transition. The domain guard throws a
   * {@link DomainException} for an already-archived repository, translated
   * here to the typed {@code REPOSITORY_ALREADY_ARCHIVED} application error
   * (never a generic runtime exception).
   */
  private void archive(Repository repository) {
    try {
      repository.archive();
    } catch (DomainException e) {
      throw new ApplicationException(ApplicationError.REPOSITORY_ALREADY_ARCHIVED,
          Map.of("reason", "already-archived", "detail", e.getMessage()));
    }
  }
}
