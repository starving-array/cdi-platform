package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

import java.util.Objects;
import java.util.Optional;

public record GetAttributionSummaryQuery(
    TenantId tenantId,
    ServiceId serviceId,
    Actor actor) {

  public GetAttributionSummaryQuery {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(actor, "actor must not be null");
  }

  public Optional<ServiceId> getServiceId() {
    return Optional.ofNullable(serviceId);
  }
}