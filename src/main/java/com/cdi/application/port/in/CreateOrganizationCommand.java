package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;

/**
 * Input contract for UC-07 CreateOrganization (application-layer.md §6).
 * Immutable; the handler checks for an existing organization by name and, on
 * a duplicate, returns the existing organization with {@code created=false}.
 *
 * <p>The organization name is the natural business key for idempotency
 * (application-layer.md §10). The {@code IdempotencyKey} is carried for
 * transport-level correlation; the semantic duplicate check is by name.
 */
public record CreateOrganizationCommand(
    String name,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public CreateOrganizationCommand {
    if (name == null || name.isBlank()) {
      throw new DomainException("Organization name cannot be blank");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    name = name.trim();
  }
}
