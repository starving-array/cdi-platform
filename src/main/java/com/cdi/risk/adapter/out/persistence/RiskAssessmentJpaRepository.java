package com.cdi.risk.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code risk_assessment} table.
 *
 * <p>Used by {@code JpaRiskAssessmentRepository} to persist the UC-03 risk
 * assessment (application-layer.md §6, data-model.md §D). The insert relies on
 * cascade to the owned {@code risk_factor} children; {@code UNIQUE
 * (tenant_id, analysis_run_id)} is the DB safety net for the one-per-run
 * invariant.
 */
public interface RiskAssessmentJpaRepository extends JpaRepository<RiskAssessmentEntity, UUID> {

  Optional<RiskAssessmentEntity> findByTenantIdAndAnalysisRunId(UUID tenantId, UUID analysisRunId);
}