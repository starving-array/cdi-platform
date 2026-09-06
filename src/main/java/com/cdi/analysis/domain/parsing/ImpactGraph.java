package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a deterministic, bounded impact graph from Part 7 dependency analysis.
 * <p>
 * The graph answers: "If this changed Java member is modified, what other
 * repository-local members could be directly affected?"
 * <p>
 * Traversal is bounded by a maximum depth (default 2). Cycles are handled
 * via visited-node tracking. Relationships are conservatively resolved using
 * the existing Resolution semantics (REPOSITORY / EXTERNAL / UNRESOLVED).
 * Fabricated or invented dependencies are never introduced.
 */
public final class ImpactGraph {

  private static final int DEFAULT_MAX_DEPTH = 2;

  private final DependencyAnalysis analysis;
  private final int maxDepth;
  private final Map<String, MemberRelation> memberRelationMap;

  /** Key format: "declaringType:memberName" */
  private static String key(String declaringType, String memberName) {
    return declaringType + ":" + memberName;
  }

  /**
   * Creates an ImpactGraph from the given dependency analysis.
   *
   * @param analysis the Part 7 dependency analysis (must not be null)
   * @param maxDepth maximum traversal depth; must be >= 0; defaults to 2
   * @throws DomainException if analysis is null or maxDepth is negative
   */
  public ImpactGraph(DependencyAnalysis analysis, int maxDepth) {
    if (analysis == null) {
      throw new DomainException("Dependency analysis cannot be null");
    }
    if (maxDepth < 0) {
      throw new DomainException("Max depth must not be negative");
    }
    this.analysis = analysis;
    this.maxDepth = maxDepth;
    this.memberRelationMap = new HashMap<>();
    buildMemberRelationMap();
  }

  /**
   * Creates an ImpactGraph with the default maximum depth of 2.
   *
   * @param analysis the Part 7 dependency analysis (must not be null)
   * @throws DomainException if analysis is null
   */
  public ImpactGraph(DependencyAnalysis analysis) {
    this(analysis, DEFAULT_MAX_DEPTH);
  }

  /** Returns the commit SHA from the analysis. */
  public String commitSha() {
    return analysis.commitSha();
  }

  /** Returns the maximum traversal depth. */
  public int maxDepth() {
    return maxDepth;
  }

  /**
   * Build the impact graph starting from all changed members.
   * <p>
   * The result contains all reachable nodes within the bounded depth,
   * the edges representing caller/callee relationships, and summary statistics.
   * <p>
   * Relationships are conservatively resolved using CallSite.resolution.
   * UNRESOLVED edges are kept but not expanded (no invented targets).
   *
   * @return the impact graph result (never null)
   */
  public ImpactGraphResult build() {
    // Identify changed members from the member relation map
    Set<String> changedKeys = new LinkedHashSet<>();
    for (Map.Entry<String, MemberRelation> entry : memberRelationMap.entrySet()) {
      if (entry.getValue().changed()) {
        changedKeys.add(entry.getKey());
      }
    }
    if (changedKeys.isEmpty()) {
      return new ImpactGraphResult(
          List.of(),
          List.of(),
          ImpactSummary.empty(),
          maxDepth);
    }

    // Bounded traversal: collect all reachable nodes and edges
    Set<String> allNodeKeys = new LinkedHashSet<>(changedKeys);
    List<ImpactGraphEdge> allEdges = new ArrayList<>();

    // Track node depth for summary computation
    Map<String, Integer> nodeDepth = new HashMap<>();

    // Queue of node keys to process, starting with changed members at depth 0
    Deque<String> queue = new ArrayDeque<>();
    Set<String> visitedNodes = new LinkedHashSet<>();

    for (String key : changedKeys) {
      queue.add(key);
      nodeDepth.put(key, 0);
    }

    // Track edges by unique key to deduplicate
    Set<String> visitedEdges = new LinkedHashSet<>();

    while (!queue.isEmpty()) {
      String currentKey = queue.poll();
      int currentDepth = nodeDepth.getOrDefault(currentKey, 0);

      if (currentDepth >= maxDepth) {
        continue;
      }

      if (visitedNodes.contains(currentKey)) {
        continue;
      }
      visitedNodes.add(currentKey);

      MemberRelation rel = memberRelationMap.get(currentKey);
      if (rel == null) {
        continue;
      }

      String declaringType = rel.declaringType();
      String memberName = rel.memberName();

      // --- Expand callees (this member calls others) ---
      for (CallSite callee : rel.callees()) {
        String calleeMember = callee.methodName();
        String calleeOwner = callee.ownerType();
        Resolution calleeRes = callee.resolution();

        String calleeKey = key(calleeOwner, calleeMember);
        String callsEdgeKey = key(declaringType, memberName) + ":" + calleeKey + ":CALLS";

        if (visitedEdges.add(callsEdgeKey)) {
          allEdges.add(ImpactGraphEdge.calls(
              memberName, calleeMember, calleeRes, callee.sourceFile(), callee.enclosingMember()));
        }

        // Also add CALLED_BY edge (callee is called by this member)
        String calledByEdgeKey = key(declaringType, memberName) + ":" + calleeKey + ":CALLED_BY";
        if (visitedEdges.add(calledByEdgeKey)) {
          allEdges.add(ImpactGraphEdge.calledBy(
              memberName, calleeMember, calleeRes, callee.sourceFile(), callee.enclosingMember()));
        }

        // Add callee node if not yet discovered
        if (!allNodeKeys.contains(calleeKey)) {
          allNodeKeys.add(calleeKey);
          nodeDepth.put(calleeKey, currentDepth + 1);
          queue.add(calleeKey);
        }
      }

      // --- Expand callers (this member is called by other members) ---
      for (CallSite caller : rel.callers()) {
        String callerEnclosingMember = caller.enclosingMember();
        String callerOwner = caller.enclosingType();

        String callerKey = key(callerOwner, callerEnclosingMember);
        String callsEdgeKey = callerKey + ":" + key(declaringType, memberName) + ":CALLS";

        if (visitedEdges.add(callsEdgeKey)) {
          allEdges.add(ImpactGraphEdge.calls(
              callerEnclosingMember, memberName, caller.resolution(), caller.sourceFile(), caller.enclosingMember()));
        }

        // Add caller node if not yet discovered
        if (!allNodeKeys.contains(callerKey)) {
          allNodeKeys.add(callerKey);
          nodeDepth.put(callerKey, currentDepth + 1);
          queue.add(callerKey);
        }

        // Add CALLED_BY edge (this member is called BY caller)
        String calledByKey = callerKey + ":" + key(declaringType, memberName) + ":CALLED_BY";
        if (visitedEdges.add(calledByKey)) {
          allEdges.add(ImpactGraphEdge.calledBy(
              memberName, callerEnclosingMember, caller.resolution(), caller.sourceFile(), caller.enclosingMember()));
        }
      }
    }

    // Compute impact summary
    int changedMembers = (int) allNodeKeys.stream()
        .filter(this::isChangedMemberKey)
        .count();

    int depth1Count = (int) allNodeKeys.stream()
        .filter(k -> nodeDepth.getOrDefault(k, 0) == 1)
        .count();

    int depth2PlusCount = (int) allNodeKeys.stream()
        .filter(k -> nodeDepth.getOrDefault(k, 0) >= 2)
        .count();

    int directlyAffectedMembers = depth1Count;
    int transitivelyAffectedMembers = depth2PlusCount - depth1Count;
    if (transitivelyAffectedMembers < 0) transitivelyAffectedMembers = 0;

    int unresolved = (int) allEdges.stream()
        .filter(e -> e.resolution() == Resolution.UNRESOLVED)
        .count();

    int repositoryLocal = (int) allEdges.stream()
        .filter(e -> e.resolution() == Resolution.REPOSITORY)
        .count();

    int external = (int) allEdges.stream()
        .filter(e -> e.resolution() == Resolution.EXTERNAL)
        .count();

    int maxReachedDepth = nodeDepth.values().stream()
        .max(Integer::compare)
        .orElse(0);

    ImpactSummary summary = new ImpactSummary(
        changedMembers,
        directlyAffectedMembers,
        transitivelyAffectedMembers,
        maxReachedDepth,
        unresolved,
        repositoryLocal,
        external,
        commitSha());

    List<ImpactNode> nodeList = allNodeKeys.stream()
        .map(k -> {
          String[] parts = k.split(":", 2);
          if (parts.length >= 2 && !parts[0].isBlank()) {
            return new ImpactNode(parts[0], parts[1]);
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return new ImpactGraphResult(
        nodeList,
        allEdges,
        summary,
        maxDepth);
  }

  /** Returns true if the given node key represents a changed member. */
  private boolean isChangedMemberKey(String key) {
    String[] parts = key.split(":", 2);
    if (parts.length < 2) return false;
    MemberRelation rel = memberRelationMap.get(key);
    return rel != null && rel.changed();
  }

  /** Map MemberRelation objects keyed by "declaringType:memberName". */
  private void buildMemberRelationMap() {
    for (FileDependencies fd : analysis.files()) {
      for (MemberRelation mr : fd.members()) {
        memberRelationMap.put(key(mr.declaringType(), mr.memberName()), mr);
      }
    }
  }
}