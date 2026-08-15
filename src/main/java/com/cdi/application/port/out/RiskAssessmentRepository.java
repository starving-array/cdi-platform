package com.cdi.application.port.out;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.risk.domain.RiskAssessment;

import java.util.Optional;

/**
 * Outbound persistence port for the RiskAssessment aggregate, used by UC-03
 * AnalyzeChange and UC-04 InvestigateRisk (application-layer.md §6). Adapters
 * persist to the {@code risk_assessment} table (data-model.md §D), scoped by
 * {@code tenant_id}.
 *
 * <p>Similarly to {@link AnalysisRunRepository} and {@code ChangeRepository},
 * {@code TenantId} lives at the persistence boundary only — the aggregate
 * carries none (data-model.md §2), so the tenant scope is supplied per call.
 * No JPA, Spring Data, SQL, or PostgreSQL types appear on this contract.
 */
public interface RiskAssessmentRepository {

  RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment);

  /**
   * Resolves the deterministic assessment for a run, scoped by tenant.
   *
   * @return the assessment for the analysis run, or empty if none exists
   */
  Optional<RiskAssessment> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId);
}