package com.cdi.risk.adapter.out.persistence;

import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.risk.domain.RiskAssessment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code RiskAssessmentRepository} port (UC-03,
 * UC-04; risk_assessment + risk_factor tables, data-model.md §D).
 *
 * <p>Persistence-side only: no domain rules here. The aggregate — assessment
 * plus owned factors — round-trips as one persisted tree via cascade,
 * matching data-model.md §7. {@code UNIQUE (tenant_id, analysis_run_id)}
 * remains the one-assessment-per-run DB backstop; an attempt to insert a
 * second assessment for the same run surfaces as
 * {@link DataIntegrityViolationException}.
 */
@Repository
public class JpaRiskAssessmentRepository implements RiskAssessmentRepository {

  private final RiskAssessmentJpaRepository riskAssessmentJpaRepository;

  public JpaRiskAssessmentRepository(RiskAssessmentJpaRepository riskAssessmentJpaRepository) {
    this.riskAssessmentJpaRepository = riskAssessmentJpaRepository;
  }

  @Override
  @Transactional
  public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
    riskAssessmentJpaRepository.findByTenantIdAndAnalysisRunId(tenantId.value(), riskAssessment.getAnalysisRunId().value())
        .ifPresent(existing -> {
            riskAssessmentJpaRepository.delete(existing);
            riskAssessmentJpaRepository.flush();
        });
    RiskAssessmentEntity entity = RiskAssessmentMapper.toEntity(tenantId, riskAssessment);
    return RiskAssessmentMapper.toDomain(riskAssessmentJpaRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RiskAssessment> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
    return riskAssessmentJpaRepository
        .findByTenantIdAndAnalysisRunId(tenantId.value(), analysisRunId.value())
        .map(RiskAssessmentMapper::toDomain);
  }
}