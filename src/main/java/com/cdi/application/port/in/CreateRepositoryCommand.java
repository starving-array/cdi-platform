package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;

/**
 * Input contract for UC-08 CreateRepository (application-layer.md §6).
 * Immutable; the handler checks for an existing repository by the
 * tenant-scoped natural key {@code (tenantId, providerType, externalId)}
 * and, on a duplicate, returns the existing repository with
 * {@code created=false}.
 *
 * <p>The natural key is the business identity of a source-control repository
 * (application-layer.md §10): the {@code providerType} + {@code externalId}
 * pair (the provider's stable repo id) scoped by {@code tenantId}. The
 * {@code IdempotencyKey} is carried for transport-level correlation; the
 * semantic duplicate check is by natural key (UC-07 organization-create
 * precedent, where the semantic check is by name).
 */
public record CreateRepositoryCommand(
    TenantId tenantId,
    Repository.ProviderType providerType,
    String externalId,
    String name,
    String url,
    String defaultBranch,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public CreateRepositoryCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (providerType == null) {
      throw new DomainException("ProviderType cannot be null");
    }
    if (externalId == null || externalId.isBlank()) {
      throw new DomainException("External ID cannot be blank");
    }
    if (name == null || name.isBlank()) {
      throw new DomainException("Repository name cannot be blank");
    }
    if (url == null || url.isBlank()) {
      throw new DomainException("Repository URL cannot be blank");
    }
    if (defaultBranch == null || defaultBranch.isBlank()) {
      throw new DomainException("Default branch cannot be blank");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    externalId = externalId.trim();
    name = name.trim();
    url = url.trim();
    defaultBranch = defaultBranch.trim();
  }
}
