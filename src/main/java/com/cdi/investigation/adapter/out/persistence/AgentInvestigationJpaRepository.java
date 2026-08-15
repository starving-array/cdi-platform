package com.cdi.investigation.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code agent_investigation} table.
 *
 * <p>Used by {@code JpaAgentInvestigationRepository} to persist the UC-04
 * investigation (application-layer.md §6, data-model.md §D). The insert relies
 * on cascade to the owned {@code investigation_finding} children;
 * {@code UNIQUE (tenant_id, analysis_run_id)} is the DB safety net for the
 * one-investigation-per-run idempotency invariant.
 */
public interface AgentInvestigationJpaRepository
    extends JpaRepository<AgentInvestigationEntity, UUID> {

  Optional<AgentInvestigationEntity> findByTenantIdAndAnalysisRunId(UUID tenantId, UUID analysisRunId);
}
