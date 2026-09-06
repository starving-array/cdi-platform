package com.cdi.analysis.domain.parsing;

/**
 * Summary statistics for a bounded impact graph analysis.
 *
 * <p>Provides deterministic, count-based metrics that downstream risk
 * scoring or monitoring can consume without needing the full graph structure.
 */
public record ImpactSummary(
    int changedMembers,
    int directlyAffectedMembers,
    int transitivelyAffectedMembers,
    int maxTraversalDepth,
    int unresolvedRelationships,
    int repositoryLocalRelationships,
    int externalRelationships,
    String commitSha, com.cdi.analysis.domain.CoverageState coverageState) {

  public ImpactSummary(int changedMembers, int directlyAffectedMembers, int transitivelyAffectedMembers, int maxTraversalDepth, int unresolvedRelationships, int repositoryLocalRelationships, int externalRelationships, String commitSha) {
    this(changedMembers, directlyAffectedMembers, transitivelyAffectedMembers, maxTraversalDepth, unresolvedRelationships, repositoryLocalRelationships, externalRelationships, commitSha, com.cdi.analysis.domain.CoverageState.FULL);
  }

  public ImpactSummary {
    changedMembers = Math.max(changedMembers, 0);
    directlyAffectedMembers = Math.max(directlyAffectedMembers, 0);
    transitivelyAffectedMembers = Math.max(transitivelyAffectedMembers, 0);
    maxTraversalDepth = Math.max(maxTraversalDepth, 0);
    unresolvedRelationships = Math.max(unresolvedRelationships, 0);
    repositoryLocalRelationships = Math.max(repositoryLocalRelationships, 0);
    externalRelationships = Math.max(externalRelationships, 0);
  }

  /** Default summary with all zeros (no impact). */
  public static ImpactSummary empty() {
    return new ImpactSummary(0, 0, 0, 0, 0, 0, 0, null, com.cdi.analysis.domain.CoverageState.FULL);
  }

  /** Build a summary with the given commit SHA. */
  public ImpactSummary withCommitSha(String sha) {
    return new ImpactSummary(changedMembers, directlyAffectedMembers,
        transitivelyAffectedMembers, maxTraversalDepth,
        unresolvedRelationships, repositoryLocalRelationships,
        externalRelationships, sha, coverageState);
  }

  /** Returns the commit SHA, or null if none set. */
  public String commitSha() {
    return commitSha;
  }

  @Override
  public String toString() {
    if (commitSha != null) {
      return commitSha;
    }
    return "ImpactSummary[changedMembers=" + changedMembers
        + ", directlyAffectedMembers=" + directlyAffectedMembers
        + ", transitivelyAffectedMembers=" + transitivelyAffectedMembers
        + ", maxTraversalDepth=" + maxTraversalDepth
        + ", unresolvedRelationships=" + unresolvedRelationships
        + ", repositoryLocalRelationships=" + repositoryLocalRelationships
        + ", externalRelationships=" + externalRelationships + "]";
  }
}
