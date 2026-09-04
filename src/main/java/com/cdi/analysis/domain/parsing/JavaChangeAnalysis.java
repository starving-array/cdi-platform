package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Structural analysis of a whole {@code CodeContext}: the exact analyzed
 * commit plus the per-file Java structural outcome.
 *
 * <p>The {@code commitSha} is carried through unchanged from the code
 * context — the structural model is always bound to the exact analyzed
 * commit, never to a branch or HEAD.
 */
public record JavaChangeAnalysis(String commitSha, List<JavaFileChange> files) {

  public JavaChangeAnalysis {
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Analysis commit SHA cannot be blank");
    }
    commitSha = commitSha.trim();
    files = files == null ? List.of() : List.copyOf(files);
  }
}
