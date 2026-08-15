package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;

/**
 * Idempotent command result for UC-08 CreateRepository: the repository
 * produced or reused by the command, plus whether it was newly created
 * (application-layer.md §6).
 *
 * <p>Carries the repository {@link RepositoryId}. The repository belongs to a
 * tenant (data-model.md §2); the tenant identity is established upstream by
 * the command's {@code TenantId} and is not duplicated on this result.
 */
public record CreateRepositoryResult(RepositoryId repositoryId, boolean created)
    implements CommandResult {

  public CreateRepositoryResult {
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
  }
}
