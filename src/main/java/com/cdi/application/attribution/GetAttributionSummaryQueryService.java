package com.cdi.application.attribution;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetAttributionSummaryQuery;
import com.cdi.application.port.out.DecisionAttributionRepository;
import com.cdi.attribution.domain.AttributionSummary;

import java.util.Objects;

public final class GetAttributionSummaryQueryService {

  private final DecisionAttributionRepository decisionAttributionRepository;

  public GetAttributionSummaryQueryService(DecisionAttributionRepository decisionAttributionRepository) {
    this.decisionAttributionRepository = Objects.requireNonNull(decisionAttributionRepository, "decisionAttributionRepository");
  }

  public AttributionSummary handle(GetAttributionSummaryQuery query) {
    // 1. Authorization: ENGINEER and TENANT_ADMIN allowed
    Actor.Role role = query.actor().role();
    if (role != Actor.Role.ENGINEER && role != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only ENGINEER or TENANT_ADMIN can view attribution summary");
    }

    return decisionAttributionRepository.getSummary(query.tenantId(), query.getServiceId());
  }
}