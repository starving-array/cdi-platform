package com.cdi.decision.adapter.out.persistence;

import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code DecisionRecordRepository} port
 * (UC-05; decision_record + decision_reason tables, data-model.md §E).
 *
 * <p>Persistence-side only: no domain rules here. The aggregate — decision
 * plus owned reasons — round-trips as one persisted tree via cascade,
 * matching data-model.md §7. {@code UNIQUE (tenant_id, analysis_run_id)}
 * remains the one-decision-per-run DB backstop (application-layer.md §10);
 * an attempt to insert a second decision for the same run surfaces as a
 * {@link org.springframework.dao.DataIntegrityViolationException}.
 */
@Repository
public class JpaDecisionRecordRepository implements DecisionRecordRepository {

  private final DecisionRecordJpaRepository decisionRecordJpaRepository;

  public JpaDecisionRecordRepository(DecisionRecordJpaRepository decisionRecordJpaRepository) {
    this.decisionRecordJpaRepository = decisionRecordJpaRepository;
  }

  @Override
  @Transactional
  public DecisionRecord save(TenantId tenantId, DecisionRecord decisionRecord) {
    DecisionRecordEntity entity = DecisionRecordMapper.toEntity(tenantId, decisionRecord);
    return DecisionRecordMapper.toDomain(decisionRecordJpaRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DecisionRecord> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
    return decisionRecordJpaRepository
        .findByTenantIdAndAnalysisRunId(tenantId.value(), analysisRunId.value())
        .map(DecisionRecordMapper::toDomain);
  }
}
