package com.cdi.application.port.out;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.investigation.domain.AgentInvestigation;

import java.util.Optional;

/**
 * Outbound persistence port for the {@code AgentInvestigation} aggregate,
 * used by UC-04 InvestigateRisk (application-layer.md §6, use-cases.md §5.3).
 * Adapters persist to the {@code agent_investigation}/{@code investigation_finding}/
 * {@code finding_evidence} tables (data-model.md §D), scoped by {@code tenant_id}.
 *
 * <p>{@code TenantId} lives at the persistence boundary only (data-model.md §2),
 * so the tenant scope is supplied per call. No JPA, Spring Data, SQL, or
 * PostgreSQL types appear on this contract.
 */
public interface AgentInvestigationRepository {

  AgentInvestigation save(TenantId tenantId, AgentInvestigation investigation);

  /**
   * Resolves the investigation for a run, scoped by tenant — used for
   * idempotent replay (an already-completed/ongoing investigation is a no-op).
   *
   * @param tenantId tenant scope
   * @param analysisRunId the analysis run investigated
   * @return the investigation for the run, or empty if none exists
   */
  Optional<AgentInvestigation> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId);

  /**
   * Resolves an investigation by id, scoped by tenant.
   */
  Optional<AgentInvestigation> findById(TenantId tenantId, InvestigationId investigationId);
}
