package com.cdi.attribution.domain;

import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;

import java.util.Objects;
import java.util.Optional;

public record AttributionSummary(
    TenantId tenantId,
    ServiceId serviceId,
    long totalAttributedDeployments,
    long accurateLowRiskCount,
    long accurateHighRiskCount,
    long underestimatedRiskCount,
    long overestimatedRiskCount,
    long unattributedCount,
    double accuracyRate) {

  public AttributionSummary {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
  }

  public Optional<ServiceId> getServiceId() {
    return Optional.ofNullable(serviceId);
  }
}