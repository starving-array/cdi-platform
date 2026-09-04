package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

/**
 * One direct method-call relation discovered during caller/callee analysis.
 *
 * @param ownerType qualified name of the type owning the called method;
 *        empty only when the owner could not be determined at all (the call
 *        is then marked UNRESOLVED)
 * @param methodName called method name
 * @param resolution REPOSITORY / EXTERNAL / UNRESOLVED
 * @param ambiguous true when exact matching is not possible (e.g. the owner
 *        overloads the method name and no symbol resolution is available);
 *        ambiguous relations are reported but must not be treated as exact
 * @param sourceFile file containing this call site
 * @param enclosingType qualified name of the type containing the call site
 * @param enclosingMember name of the member containing the call site
 */
public record CallSite(
    String ownerType,
    String methodName,
    Resolution resolution,
    boolean ambiguous,
    String sourceFile,
    String enclosingType,
    String enclosingMember) {

  public CallSite {
    if (methodName == null || methodName.isBlank()) {
      throw new DomainException("Called method name cannot be blank");
    }
    if (resolution == null) {
      throw new DomainException("Resolution cannot be null");
    }
    ownerType = ownerType == null ? "" : ownerType;
    sourceFile = sourceFile == null ? "" : sourceFile;
    enclosingType = enclosingType == null ? "" : enclosingType;
    enclosingMember = enclosingMember == null ? "" : enclosingMember;
  }
}
