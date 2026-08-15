package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;

/**
 * Input contract for UC-09 CreateService (application-layer.md §6).
 * Immutable; the handler checks for an existing service by the
 * tenant-scoped natural key {@code (tenantId, name)} and, on a duplicate,
 * returns the existing service with {@code created=false}.
 *
 * <p>The natural key is the business identity of a system-context service
 * (application-layer.md §10): the service {@code name} scoped by
 * {@code tenantId}. The {@code IdempotencyKey} is carried for
 * transport-level correlation; the semantic duplicate check is by natural
 * key (UC-07 organization-create precedent, where the semantic check is by
 * name).
 *
 * <p>The {@code owner} field is nullable per the existing
 * {@code Service} domain contract (the constructor accepts a null owner);
 * this command therefore does not reject a null owner. {@code CriticalityTier}
 * is an enum, so whitespace validation is not applicable to it.
 */
public record CreateServiceCommand(
    TenantId tenantId,
    String name,
    CriticalityTier criticalityTier,
    String owner,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public CreateServiceCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (name == null || name.isBlank()) {
      throw new DomainException("Service name cannot be blank");
    }
    if (criticalityTier == null) {
      throw new DomainException("CriticalityTier cannot be null");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    name = name.trim();
    if (owner != null) {
      owner = owner.trim();
    }
  }
}
