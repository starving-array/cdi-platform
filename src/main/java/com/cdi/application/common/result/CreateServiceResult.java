package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;

/**
 * Idempotent command result for UC-09 CreateService: the service
 * produced or reused by the command, plus whether it was newly
 * created (application-layer.md §6).
 *
 * <p>Carries the service {@link ServiceId}. The service belongs to a
 * tenant (data-model.md §2); the tenant identity is established upstream by
 * the command's {@code TenantId} and is not duplicated on this result.
 */
public record CreateServiceResult(ServiceId serviceId, boolean created)
    implements CommandResult {

  public CreateServiceResult {
    if (serviceId == null) {
      throw new DomainException("ServiceId cannot be null");
    }
  }
}
