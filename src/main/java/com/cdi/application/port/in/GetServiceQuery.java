package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;

/**
 * Input contract for UC-15 GetService (application-layer.md §6).
 */
public record GetServiceQuery(ServiceId serviceId) {

  public GetServiceQuery {
    if (serviceId == null) {
      throw new DomainException("ServiceId cannot be null");
    }
  }
}