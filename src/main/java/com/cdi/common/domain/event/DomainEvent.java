package com.cdi.common.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal contract for all Domain Events.
 * Keeps the domain isolated from specific messaging frameworks (e.g., Spring ApplicationEvent, Kafka).
 */
public interface DomainEvent {
    
    /**
     * @return The unique identifier of this specific event occurrence.
     */
    UUID eventId();

    /**
     * @return When the event occurred (always UTC).
     */
    Instant occurredOn();
}
