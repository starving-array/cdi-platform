package com.cdi.analysis.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.TenantId;

/**
 * Maps between the {@code AnalysisRun} aggregate and its persistence
 * representation.
 *
 * <p>Horizontal conversion only: the immutable {@code CodeSnapshot} flattens to
 * {@code commit_sha}/{@code branch}; the optional {@code AnalysisFailure} maps
 * to three nullable columns; the tenant scope is stored from the port call
 * because {@code TenantId} does not live on the aggregate (data-model.md §2).
 */
final class AnalysisRunMapper {

  private AnalysisRunMapper() {
    // utility class
  }

  static AnalysisRunEntity toEntity(TenantId tenantId, AnalysisRun run) {
    AnalysisRunEntity entity = new AnalysisRunEntity();
    entity.setId(run.getId().value());
    entity.setTenantId(tenantId.value());
    entity.setChangeId(run.getChangeId().value());
    entity.setCommitSha(run.getCodeSnapshot().commitSha());
    entity.setBranch(run.getCodeSnapshot().branch());
    entity.setStatus(run.getStatus().name());
    run.getFailureInfo().ifPresent(failure -> {
      entity.setFailureCategory(failure.category().name());
      entity.setFailureCode(failure.failureCode());
      entity.setFailedAt(failure.failedAt());
    });
    entity.setCreatedAt(run.getCreatedAt());
    run.getCompletedAt().ifPresent(entity::setCompletedAt);
    return entity;
  }

  static AnalysisRun toDomain(AnalysisRunEntity entity) {
    AnalysisFailure failure = null;
    if (entity.getFailureCategory() != null) {
      failure = new AnalysisFailure(
          AnalysisFailure.FailureCategory.valueOf(entity.getFailureCategory()),
          entity.getFailureCode(),
          entity.getFailedAt());
    }
    return AnalysisRun.restore(
        new AnalysisRunId(entity.getId()),
        new ChangeId(entity.getChangeId()),
        new CodeSnapshot(entity.getCommitSha(), entity.getBranch()),
        AnalysisRun.Status.valueOf(entity.getStatus()),
        failure,
        entity.getCreatedAt(),
        entity.getCompletedAt());
  }
}