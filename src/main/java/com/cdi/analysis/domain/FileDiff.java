package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;

/**
 * Value object describing one changed file in a code snapshot.
 *
 * <p>Produced by {@code SourceControlPort.getDiff} during snapshot
 * normalization (application-layer.md §5, determination A) and consumed by
 * the deterministic risk engine and evidence search. It belongs with the
 * snapshot side of an {@code AnalysisRun}, not on the {@code Change}
 * aggregate.
 */
public record FileDiff(String path, int additions, int deletions, ChangeType changeType) {

  public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED
  }

  public FileDiff {
    if (path == null || path.isBlank()) {
      throw new DomainException("File path cannot be blank");
    }
    if (additions < 0) {
      throw new DomainException("Additions cannot be negative");
    }
    if (deletions < 0) {
      throw new DomainException("Deletions cannot be negative");
    }
    if (changeType == null) {
      throw new DomainException("Change type cannot be null");
    }
    path = path.trim();
  }
}