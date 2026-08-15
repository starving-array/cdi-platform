package com.cdi.investigation.adapter.out.persistence;

import com.cdi.application.port.out.AgentInvestigationRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.investigation.domain.AgentInvestigation;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code AgentInvestigationRepository} port
 * (UC-04; agent_investigation + investigation_finding tables, data-model.md §D).
 *
 * <p>Persistence-side only: no domain rules here. The aggregate — investigation
 * plus owned findings — round-trips as one persisted tree via cascade.
 * {@code UNIQUE (tenant_id, analysis_run_id)} is the DB safety net for the
 * one-investigation-per-run idempotency invariant.
 */
@Repository
public class JpaAgentInvestigationRepository implements AgentInvestigationRepository {

  private final AgentInvestigationJpaRepository agentInvestigationJpaRepository;

  public JpaAgentInvestigationRepository(
      AgentInvestigationJpaRepository agentInvestigationJpaRepository) {
    this.agentInvestigationJpaRepository = agentInvestigationJpaRepository;
  }

  @Override
  @Transactional
  public AgentInvestigation save(TenantId tenantId, AgentInvestigation investigation) {
    AgentInvestigationEntity entity =
        AgentInvestigationMapper.toEntity(tenantId, investigation);
    return AgentInvestigationMapper.toDomain(agentInvestigationJpaRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AgentInvestigation> findByAnalysisRunId(
      TenantId tenantId, AnalysisRunId analysisRunId) {
    return agentInvestigationJpaRepository
        .findByTenantIdAndAnalysisRunId(tenantId.value(), analysisRunId.value())
        .map(AgentInvestigationMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AgentInvestigation> findById(
      TenantId tenantId, InvestigationId investigationId) {
    return agentInvestigationJpaRepository.findById(investigationId.value())
        .filter(entity -> entity.getTenantId().equals(tenantId.value()))
        .map(AgentInvestigationMapper::toDomain);
  }
}
