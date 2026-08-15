package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

/**
 * Input contract for UC-02 RequestAnalysis (application-layer.md §6): manual/
 * internal re-analysis of a change snapshot. Idempotent — the handler always
 * resolves by {@code (tenant, change, commit)} and never duplicates a run;
 * a FAILED run is re-enqueued (retry).
 */
public record RequestAnalysisCommand(
    TenantId tenantId,
    ChangeId changeId,
    String commitSha,
    Actor actor,
    IdempotencyKey idempotencyKey) {

  public RequestAnalysisCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Commit SHA cannot be blank");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (idempotencyKey == null) {
      throw new DomainException("Idempotency key cannot be null");
    }
    commitSha = commitSha.trim();
  }
}