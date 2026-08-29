package com.cdi.common.adapter.out.event;

import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.common.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based implementation of DomainEventPublisher.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  /**
   * Constructs the publisher.
   *
   * @param applicationEventPublisher the spring publisher
   */
  public SpringDomainEventPublisher(final ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(final DomainEvent event) {
    applicationEventPublisher.publishEvent(event);
  }
}