package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.systemcontext.domain.Service;

/**
 * Command result for the P2 DeprecateService capability: the service
 * id and the resulting {@link Service.Status} after the deprecation was
 * persisted (application-layer.md §6).
 *
 * <p>The status returned is the post-transition state ({@code DEPRECATED});
 * the result carries the service identity (organization-repository-service-domain.md,
 * data-model.md §3.A).
 */
public record DeprecateServiceResult(
    ServiceId serviceId,
    Service.Status status) implements CommandResult {

  public DeprecateServiceResult {
    if (serviceId == null) {
      throw new DomainException("ServiceId cannot be null");
    }
    if (status == null) {
      throw new DomainException("Status cannot be null");
    }
  }
}