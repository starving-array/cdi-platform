package com.cdi.analysis.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA/PostgreSQL adapter for the {@code AnalysisRunRepository} port (UC-01,
 * UC-02, UC-03; analysis_run table, data-model.md §B).
 *
 * <p>Persistence-side only: no domain rules here. The exact-snapshot lookup
 * that drives UC-01/UC-02 idempotent reuse delegates to the Spring Data derived
 * query; {@code UNIQUE (tenant_id, change_id, commit_sha)} remains the DB
 * safety net. {@link #claim} is the atomic {@code QUEUED→RUNNING} CAS used by
 * the UC-03 worker (§11.2); {@link #findById} resolves the run and its tenant
 * (the worker payload carries only the run id). Failures bubble up as Spring
 * {@code DataAccessException}s per the documented persistence policy;
 * concurrent duplicate insert violation surfaces as
 * {@link DataIntegrityViolationException}.
 */
@Repository
public class JpaAnalysisRunRepository implements AnalysisRunRepository {

  private final AnalysisRunJpaRepository analysisRunJpaRepository;

  public JpaAnalysisRunRepository(AnalysisRunJpaRepository analysisRunJpaRepository) {
    this.analysisRunJpaRepository = analysisRunJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
      TenantId tenantId, ChangeId changeId, String commitSha) {
    return analysisRunJpaRepository
        .findByTenantIdAndChangeIdAndCommitSha(
            tenantId.value(), changeId.value(), commitSha)
        .map(AnalysisRunMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId) {
    return analysisRunJpaRepository.findById(analysisRunId.value())
        .map(entity -> new AnalysisRunContext(
            new TenantId(entity.getTenantId()), AnalysisRunMapper.toDomain(entity)));
  }

  @Override
  @Transactional
  public boolean claim(AnalysisRunId analysisRunId) {
    return analysisRunJpaRepository.claimQueuedToRunning(analysisRunId.value()) > 0;
  }

  @Override
  @Transactional
  public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
    AnalysisRunEntity entity = AnalysisRunMapper.toEntity(tenantId, run);
    return AnalysisRunMapper.toDomain(analysisRunJpaRepository.save(entity));
  }
}