package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

/**
 * A referenced type name with its resolution outcome.
 *
 * @param name the name as written in the source (simple or qualified)
 * @param qualifiedName the best deterministic qualified name — import-qualified,
 *        package-qualified, or the raw name when only the simple name is known;
 *        equal to {@code name} when UNRESOLVED
 */
public record ResolvedTypeRef(String name, String qualifiedName, Resolution resolution) {

  public ResolvedTypeRef {
    if (name == null || name.isBlank()) {
      throw new DomainException("Referenced type name cannot be blank");
    }
    if (resolution == null) {
      throw new DomainException("Resolution cannot be null");
    }
    qualifiedName = qualifiedName == null || qualifiedName.isBlank() ? name : qualifiedName;
  }
}
