package com.cdi.application.port.out;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

/**
 * Change + run context handed to the investigation agent (ports-and-adapters.md
 * §2.4). {@code analysisRunId} is the primary correlation ID.
 */
public record AgentContext(
    TenantId tenantId,
    ChangeId changeId,
    AnalysisRunId analysisRunId,
    String commitSha) {

  public AgentContext {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
    commitSha = commitSha.trim();
  }
}