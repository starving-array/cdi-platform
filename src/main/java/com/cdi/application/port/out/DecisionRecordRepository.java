package com.cdi.application.port.out;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;

import java.util.Optional;

/**
 * Outbound persistence port for the DecisionRecord aggregate, used by UC-05
 * policy evaluation (application-layer.md §6, data-model.md §E). Adapters
 * persist to the {@code decision_record} table (data-model.md §E), scoped by
 * {@code tenant_id}.
 *
 * <p>Tenant isolation follows data-model.md §2/§5: {@code TenantId} lives at
 * the persistence boundary only (the aggregate does not carry a separate scope
 * distinction here, but every lookup is tenant-scoped and the
 * {@code UNIQUE (tenant_id, analysis_run_id)} DB backstop permits one decision
 * per run). No JPA, Spring Data, SQL, or PostgreSQL types appear on this
 * contract.
 */
public interface DecisionRecordRepository {

  DecisionRecord save(TenantId tenantId, DecisionRecord decisionRecord);

  /**
   * Resolves the persisted decision for a run, scoped by tenant. Used by the
   * UC-05 handler for the idempotent "already evaluated" no-op check
   * (application-layer.md §10) — decision generation for a run that already
   * has a decision is a replay-safe no-op.
   *
   * @return the decision for the analysis run, or empty if none exists
   */
  Optional<DecisionRecord> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId);
}
