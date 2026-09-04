package com.cdi.analysis.domain.parsing;

import java.util.List;
import java.util.Objects;

/**
 * Result of a bounded impact graph build operation.
 */
public class ImpactGraphResult {
  public final List<ImpactNode> nodes;
  public final List<ImpactGraphEdge> edges;
  public final ImpactSummary summary;
  public final int maxDepth;

  public ImpactGraphResult(List<ImpactNode> nodes, List<ImpactGraphEdge> edges,
                           ImpactSummary summary, int maxDepth) {
    this.nodes = Objects.requireNonNull(nodes);
    this.edges = Objects.requireNonNull(edges);
    this.summary = Objects.requireNonNull(summary);
    this.maxDepth = maxDepth;
  }
}