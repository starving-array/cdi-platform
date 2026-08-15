package com.cdi.common.domain.event;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical domain event emitted when a new organization (tenant) is created
 * (bounded-contexts.md §1, application-layer.md §12).
 *
 * <p>Carrier shape follows the existing event envelope style: {@code eventId},
 * {@code occurredOn}, {@code tenantId}, {@code name}. Published by the
 * application layer through {@code DomainEventPublisher} only when new state
 * is actually created; idempotent reuse of an existing organization publishes
 * nothing.
 */
public record TenantCreated(
    UUID eventId,
    Instant occurredOn,
    TenantId tenantId,
    String name) implements DomainEvent {

  public TenantCreated {
    if (eventId == null) {
      throw new DomainException("Event ID cannot be null");
    }
    if (occurredOn == null) {
      throw new DomainException("Occurred-on timestamp cannot be null");
    }
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (name == null || name.isBlank()) {
      throw new DomainException("Organization name cannot be blank");
    }
    name = name.trim();
  }

  /**
   * Factory capturing the event instant at creation time.
   */
  public static TenantCreated create(TenantId tenantId, String name) {
    return new TenantCreated(UUID.randomUUID(), Instant.now(), tenantId, name);
  }
}
