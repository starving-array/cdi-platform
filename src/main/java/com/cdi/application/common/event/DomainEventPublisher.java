package com.cdi.application.common.event;

import com.cdi.common.domain.event.DomainEvent;

/**
 * Transactional-outbox contract for publishing internal domain events
 * (application-layer.md §12).
 *
 * <p>Implementations insert the event row in the same transaction as the
 * state change that produced it, guaranteeing "event iff state committed".
 * The domain layer only declares {@link DomainEvent}; it never publishes.
 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);
}