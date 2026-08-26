package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for the P2 ArchiveRepository capability
 * (application-layer.md §6). Synchronous, TENANT_ADMIN-only state change that
 * transitions the repository from {@code ACTIVE} to {@code ARCHIVED}
 * (organization-repository-service-domain.md, {@code Repository.archive()}).
 *
 * <p>The repository is a tenant-scoped child aggregate, so the command carries
 * both the scoping {@code tenantId} and the target {@code repositoryId}
 * (data-model.md §2/§5). This command is <b>non-idempotent</b> and therefore
 * carries no {@code IdempotencyKey} — a repeated archive of an already
 * archived repository is rejected ({@code REPOSITORY_ALREADY_ARCHIVED}),
 * never replayed (application-layer.md §10).
 */
public record ArchiveRepositoryCommand(
    TenantId tenantId,
    RepositoryId repositoryId,
    Actor actor) {

  public ArchiveRepositoryCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
  }
}
