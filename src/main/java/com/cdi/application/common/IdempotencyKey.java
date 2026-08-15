package com.cdi.application.common;

import com.cdi.common.domain.exception.DomainException;

/**
 * Value object wrapping the computed idempotency key of a command
 * (application-layer.md §10).
 *
 * <p>The key computation itself is a use-case concern (handlers), so this
 * contract only guards the opaque key value. Persistence keeps the key as the
 * final race-safe uniqueness backstop.
 */
public record IdempotencyKey(String key) {

  public IdempotencyKey {
    if (key == null || key.isBlank()) {
      throw new DomainException("Idempotency key cannot be blank");
    }
    key = key.trim();
  }
}