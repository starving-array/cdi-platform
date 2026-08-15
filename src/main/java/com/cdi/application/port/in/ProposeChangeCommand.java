package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for UC-01 ProposeChange (application-layer.md §6).
 * Immutable; the handler verifies the provided idempotency key and, on a
 * duplicate intake, returns the existing run with {@code created=false}.
 */
public record ProposeChangeCommand(
    TenantId tenantId,
    RepositoryId repositoryId,
    String providerChangeId,
    String commitSha,
    String branch,
    String title,
    String description,
    String author,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public ProposeChangeCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
    if (providerChangeId == null || providerChangeId.isBlank()) {
      throw new DomainException("Provider change ID cannot be blank");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    providerChangeId = providerChangeId.trim();
    commitSha = commitSha.trim();
    branch = branch != null ? branch.trim() : "";
    title = title != null ? title.trim() : "";
    description = description != null ? description.trim() : "";
    author = author != null ? author.trim() : "";
  }
}