package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * The code context of one {@link AnalysisRun}: the explicit, commit-bound
 * representation of the repository state CDI analyzed.
 *
 * <p>The {@code commitSha} is authoritative and must equal
 * {@link CodeSnapshot#commitSha()}; it is never resolved from a branch or
 * HEAD. Changed files compose the existing {@link FileDiff} VO rather than
 * duplicating path/additions/deletions/changeType/patch.
 */
public record CodeContext(String commitSha, List<ChangedFileSource> changedFiles, CoverageState coverageState) {

  public CodeContext(String commitSha, List<ChangedFileSource> changedFiles) {
    this(commitSha, changedFiles, CoverageState.FULL);
  }

  public CodeContext {
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("CodeContext commit SHA cannot be blank");
    }
    commitSha = commitSha.trim();
    changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
  }
}
