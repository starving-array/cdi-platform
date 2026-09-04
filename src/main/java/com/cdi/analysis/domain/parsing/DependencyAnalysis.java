package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Caller/callee and reference-resolution result for one analyzed code context.
 *
 * <p>Carries the exact analyzed commit SHA verbatim; produced by
 * {@code JavaDependencyAnalyzer} from the same in-context sources (no
 * network, no LLM, fully deterministic).
 */
public record DependencyAnalysis(String commitSha, List<FileDependencies> files) {

  public DependencyAnalysis {
    if (commitSha == null || commitSha.isBlank()) {
      throw new DomainException("Dependency analysis commit SHA cannot be blank");
    }
    commitSha = commitSha.trim();
    files = files == null ? List.of() : List.copyOf(files);
  }
}
