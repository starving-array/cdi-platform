package com.cdi.application.repository;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetRepositoryQuery;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;

import java.util.Objects;

/**
 * Application query service UC-14 GetRepository (application-layer.md §6/§4,
 * api-contract.md §2.1). Synchronous, read-only: returns the
 * {@link Repository} aggregate backing the identifier and serves as the P0
 * support query exposing repository/provider metadata (application-layer.md
 * §4 — "no admin writes needed"). Never mutates state, never opens a write
 * transaction, never calls an external port, and is never idempotency-keyed
 * (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Lookup</b>: by primary {@link GetRepositoryQuery repositoryId}; the
 * tenant is resolved from the request context (data-model.md §2 — the
 * repository id alone is the worker/API payload).
 *
 * <p><b>Tenant isolation</b> (data-model.md §2/§5): the lookup is scoped to
 * the context tenant via
 * {@link RepositoryRepository#findByTenantIdAndId} — a repository that does
 * not belong to the context tenant resolves to
 * {@link ApplicationError#REPOSITORY_NOT_FOUND}; no cross-tenant read is
 * possible and no unscoped lookup is used.
 *
 * <p><b>Role guard</b> (application-layer.md §9.2, use-cases.md §2): UC-14's
 * actor is {@code ENGINEER} or {@code TENANT_ADMIN} (a supporting query for
 * both the engineer console and the workspace-owner UI).
 */
public final class GetRepositoryQueryService {

  private final RepositoryRepository repositoryRepository;

  public GetRepositoryQueryService(RepositoryRepository repositoryRepository) {
    this.repositoryRepository = Objects.requireNonNull(repositoryRepository, "RepositoryRepository");
  }

  public Repository handle(GetRepositoryQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    return repositoryRepository.findByTenantIdAndId(tenantId, query.repositoryId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.REPOSITORY_NOT_FOUND));
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}