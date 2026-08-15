package com.cdi.analysis.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code analysis_run} table.
 *
 * <p>Derived findBy query backing the {@code AnalysisRunRepository} exact
 * snapshot lookup ({@code tenant_id, change_id, commit_sha}) used by UC-01
 * idempotent reuse (application-layer.md §10, data-model.md §5), plus the
 * natural-id read for UC-03. The {@code @Modifying} claim query is the atomic
 * {@code QUEUED→RUNNING} CAS used by the UC-03 worker (application-layer.md
 * §11.2); zero rows affected means the run is owned by another worker.
 */
public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, UUID> {

  Optional<AnalysisRunEntity> findByTenantIdAndChangeIdAndCommitSha(
      UUID tenantId, UUID changeId, String commitSha);

  long countByTenantIdAndChangeId(UUID tenantId, UUID changeId);

  @Modifying
  @Query("UPDATE AnalysisRunEntity r SET r.status = 'RUNNING' "
      + "WHERE r.id = :id AND r.status = 'QUEUED'")
  int claimQueuedToRunning(@Param("id") UUID id);
}