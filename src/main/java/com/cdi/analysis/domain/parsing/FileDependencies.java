package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Dependency/call-relation view of one analyzed file: the types it declares,
 * the types it references (resolved), and the caller/callee relations of its
 * members.
 */
public record FileDependencies(
    String path,
    List<String> declaredTypes,
    List<ResolvedTypeRef> references,
    List<MemberRelation> members) {

  public FileDependencies {
    if (path == null || path.isBlank()) {
      throw new DomainException("File path cannot be blank");
    }
    declaredTypes = declaredTypes == null ? List.of() : List.copyOf(declaredTypes);
    references = references == null ? List.of() : List.copyOf(references);
    members = members == null ? List.of() : List.copyOf(members);
  }
}
