package com.cdi.application.port.out;

import com.cdi.analysis.domain.FileDiff;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;

import java.util.List;

/**
 * Outbound port abstracting interactions with version control systems
 * (GitHub/GitLab/Bitbucket) (ports-and-adapters.md §2.1, application-layer.md
 * §7).
 *
 * <p>The application layer never imports provider SDK classes; adapters
 * implement this interface and translate provider responses into domain and
 * application types.
 *
 * <p><b>Failure policy:</b> rate-limit/5xx are retryable (exponential backoff,
 * max 3). When source data cannot be obtained the analysis must eventually
 * hard-fail (run FAILED, manual review required).
 */
public interface SourceControlPort {

  ChangeMetadata getChangeMetadata(TenantId tenantId, RepositoryId repositoryId, String providerChangeId);

  List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha);

  /**
   * Retrieves the raw bytes of a single file at the EXACT requested commit.
   *
   * <p>The {@code commitSha} is authoritative: implementations must query the
   * requested SHA and MUST NOT fall back to the default branch, a branch HEAD,
   * or any other ref when the requested commit fails.
   *
   * <p>Raw bytes are returned so callers decide text decoding; binary files are
   * therefore handled safely. A missing file, a directory path, or unusable
   * content is surfaced as a domain exception from the adapter.
   */
  List<String> listFiles(TenantId tenantId, RepositoryId repositoryId, String commitSha);

  byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path, String commitSha);

  void publishStatusCheck(
      TenantId tenantId,
      RepositoryId repositoryId,
      String commitSha,
      DecisionOutcome outcome,
      List<DecisionReason> reasons,
      String detailsUrl);
}
