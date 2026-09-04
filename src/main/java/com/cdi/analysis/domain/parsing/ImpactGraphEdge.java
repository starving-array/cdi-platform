package com.cdi.analysis.domain.parsing;

/**
 * Edge type representing a relationship in the bounded impact graph.
 */
public record ImpactGraphEdge(
    String edgeType,
    String sourceMember,
    String targetMember,
    Resolution resolution,
    String sourceFile,
    String enclosingMember) {

  public ImpactGraphEdge {
    if (edgeType == null || edgeType.isBlank()) {
      throw new IllegalArgumentException("Edge type cannot be blank");
    }
    if (sourceMember == null || sourceMember.isBlank()) {
      throw new IllegalArgumentException("Source member cannot be blank");
    }
    if (targetMember == null || targetMember.isBlank()) {
      throw new IllegalArgumentException("Target member cannot be blank");
    }
  }

  /** Edge representing the changed member itself. */
  public static ImpactGraphEdge changed(String member) {
    return new ImpactGraphEdge("CHANGED", member, member, Resolution.REPOSITORY, "", "");
  }

  /** Edge representing a call from source to target (source CALLS target). */
  public static ImpactGraphEdge calls(
      String sourceMember, String targetMember, Resolution resolution, String sourceFile, String enclosingMember) {
    return new ImpactGraphEdge("CALLS", sourceMember, targetMember, resolution, sourceFile, enclosingMember);
  }

  /** Edge representing a call from source to target that CALLS source (CALLED_BY direction). */
  public static ImpactGraphEdge calledBy(
      String sourceMember, String targetMember, Resolution resolution, String sourceFile, String enclosingMember) {
    return new ImpactGraphEdge("CALLED_BY", sourceMember, targetMember, resolution, sourceFile, enclosingMember);
  }

  /** Edge representing a type dependency from source to target. */
  public static ImpactGraphEdge typeDependency(
      String sourceMember, String targetMember) {
    return new ImpactGraphEdge("TYPE_DEPENDENCY", sourceMember, targetMember, Resolution.EXTERNAL, "", "");
  }
}