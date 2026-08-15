package com.cdi.decision.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code decision_record} table.
 *
 * <p>Used by {@code JpaDecisionRecordRepository} to persist the UC-05 policy
 * decision (application-layer.md §6, data-model.md §E). The insert relies on
 * cascade to the owned {@code decision_reason} children;
 * {@code UNIQUE (tenant_id, analysis_run_id)} is the DB safety net for the
 * one-decision-per-run invariant (application-layer.md §10).
 */
public interface DecisionRecordJpaRepository extends JpaRepository<DecisionRecordEntity, UUID> {

  Optional<DecisionRecordEntity> findByTenantIdAndAnalysisRunId(UUID tenantId, UUID analysisRunId);
}
