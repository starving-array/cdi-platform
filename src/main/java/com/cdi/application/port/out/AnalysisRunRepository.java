package com.cdi.application.port.out;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound persistence port for the AnalysisRun aggregate, used by UC-01
 * ProposeChange, UC-02 RequestAnalysis, and UC-03 AnalyzeChange
 * (application-layer.md §6, §10, §11). Adapters persist to the
 * {@code analysis_run} table (data-model.md §B) whose uniqueness backstop is
 * {@code UNIQUE(tenant_id, change_id, commit_sha)}.
 *
 * <p>UC-03 adds two worker-facing operations:
 * <ul>
 *   <li>{@link #findById} — resolves a run by its id together with its tenant
 *       (the worker payload carries only the run id, data-model.md §2 keeps
 *       {@code TenantId} off the aggregate);</li>
 *   <li>{@link #claim} — the atomic {@code QUEUED→RUNNING} CAS claim
 *       (application-layer.md §11.2); a false result means another worker owns
 *       the run and this is an idempotent no-op.</li>
 * </ul>
 *
 * <p>No JPA, Spring Data, SQL, or PostgreSQL types appear on this contract.
 */
public interface AnalysisRunRepository {

  Optional<AnalysisRun> findByTenantAndChangeAndCommit(
      TenantId tenantId, ChangeId changeId, String commitSha);

  Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId);

  /**
   * Atomically transitions the run from {@code QUEUED} to {@code RUNNING}.
   *
   * @param analysisRunId the run to claim
   * @return {@code true} if this call performed the transition; {@code false}
   *     if the run is not {@code QUEUED} (someone else owns it / replay-safe)
   */
  boolean claim(AnalysisRunId analysisRunId);

  /**
   * Lists every analysis run for a change, scoped by tenant — the UC-11
   * GetChange run history (application-layer.md §6 UC-11, use-cases.md §6.1).
   * Ordered by {@code createdAt} ascending (oldest first) with {@code id} as
   * the deterministic tie-breaker.
   *
   * @return the change's runs, oldest first; empty if the change has no runs
   *     yet
   */
  List<AnalysisRunContext> findByTenantAndChange(TenantId tenantId, ChangeId changeId);

  AnalysisRun save(TenantId tenantId, AnalysisRun run);
}