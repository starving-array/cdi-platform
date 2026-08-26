package com.cdi.application.change;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListChangesQuery;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Application query service P2 ListChanges (application-layer.md §6).
 * Synchronous, read-only: lists every {@link Change} belonging to the tenant,
 * optionally filtered by repository. Never mutates state, never opens a write
 * transaction, never calls an external port, and is never idempotency-keyed
 * (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Lookup</b>: by context {@code TenantId} and optional
 * {@code repositoryId} filter. If {@code repositoryId} is supplied, the
 * repository is verified against the context tenant via
 * {@link RepositoryRepository#findByTenantIdAndId}; a non-existent or
 * cross-tenant repository resolves to
 * {@link ApplicationError#REPOSITORY_NOT_FOUND}. Changes are queried directly
 * from the database ordered by {@code createdAt DESC, id DESC}.
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): reads are strictly
 * tenant-scoped; a tenant without changes returns an empty list.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2):
 * {@code ENGINEER} and {@code TENANT_ADMIN} may execute this query.
 */
public final class ListChangesQueryService {

  private final ChangeRepository changeRepository;
  private final RepositoryRepository repositoryRepository;

  public ListChangesQueryService(
      ChangeRepository changeRepository,
      RepositoryRepository repositoryRepository) {
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.repositoryRepository = Objects.requireNonNull(repositoryRepository, "RepositoryRepository");
  }

  public List<Change> handle(ListChangesQuery query, TenantId tenantId, Actor actor) {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (query == null) {
      throw new DomainException("Query cannot be null");
    }
    requireRole(actor);

    if (query.repositoryId().isPresent()) {
      var repoId = query.repositoryId().get();
      repositoryRepository.findByTenantIdAndId(tenantId, repoId)
          .orElseThrow(() -> new ApplicationException(ApplicationError.REPOSITORY_NOT_FOUND));
      return changeRepository.findAllByTenantIdAndRepositoryId(tenantId, repoId);
    }

    return changeRepository.findAllByTenantId(tenantId);
  }

  private void requireRole(Actor actor) {
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}