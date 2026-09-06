package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;

import java.util.Optional;

/**
 * Value object describing one changed file in a code snapshot.
 *
 * <p>Produced by {@code SourceControlPort.getDiff} during snapshot
 * normalization (application-layer.md §5, determination A) and consumed by
 * the deterministic risk engine and evidence search. It belongs with the
 * snapshot side of an {@code AnalysisRun}, not on the {@code Change}
 * aggregate.
 */
public record FileDiff(String path, int additions, int deletions, ChangeType changeType, String patch) {

  public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    UNMODIFIED
  }

  /**
   * Backward-compatible constructor without patch content (for source-control
   * adapters that do not provide textual patches).
   */
  public FileDiff(String path, int additions, int deletions, ChangeType changeType) {
    this(path, additions, deletions, changeType, null);
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

  /**
   * Convenience accessor: the raw textual patch for this file, if the
   * source-control provider supplied one (e.g. GitHub's {@code patch} field).
   * Empty when the provider omitted it (binary files, large diffs, unsupported
   * encodings). The record's {@code patch()} returns the raw, possibly-null
   * value; this wraps it for optional chaining.
   */
  public Optional<String> patchOptional() {
    return Optional.ofNullable(patch);
  }
}