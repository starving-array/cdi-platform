package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;

import java.util.UUID;

/**
 * Strongly typed identifier for an {@code AgentInvestigation} (domain-model.md §F).
 */
public record InvestigationId(UUID value) {

  public InvestigationId {
    if (value == null) {
      throw new DomainException("InvestigationId value cannot be null");
    }
  }

  public static InvestigationId generate() {
    return new InvestigationId(UUID.randomUUID());
  }

  public static InvestigationId fromString(String uuid) {
    try {
      return new InvestigationId(UUID.fromString(uuid));
    } catch (IllegalArgumentException e) {
      throw new DomainException("Invalid UUID format for InvestigationId: " + uuid, e);
    }
  }
}
