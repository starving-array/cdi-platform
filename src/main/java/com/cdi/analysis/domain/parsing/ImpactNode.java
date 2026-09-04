package com.cdi.analysis.domain.parsing;

/**
 * Node identifier in the bounded impact graph.
 * A node represents a Java member (method or constructor) identified by
 * its declaring type's qualified name and the member name.
 * Deduplication is based on this pair.
 */
public record ImpactNode(
    String declaringType,
    String memberName) {

  public ImpactNode {
    if (declaringType == null || declaringType.isBlank()) {
      throw new IllegalArgumentException("Declaring type cannot be blank");
    }
    if (memberName == null || memberName.isBlank()) {
      throw new IllegalArgumentException("Member name cannot be blank");
    }
  }

  /** Node for a changed member. */
  public static ImpactNode changed(String declaringType, String memberName) {
    return new ImpactNode(declaringType, memberName);
  }

  /** Node for an affected/transitively impacted member. */
  public static ImpactNode affected(String declaringType, String memberName) {
    return new ImpactNode(declaringType, memberName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImpactNode)) return false;
    ImpactNode that = (ImpactNode) o;
    return declaringType.equals(that.declaringType) &&
        memberName.equals(that.memberName);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(declaringType, memberName);
  }
}