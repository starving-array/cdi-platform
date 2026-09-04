package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Caller/callee relations for one member (method or constructor) of a type.
 *
 * <p>Both sides are bounded to the files present in the analyzed code
 * context; the full repository graph is PART 8's concern.
 *
 * @param declaringType qualified name of the type declaring the member
 * @param memberName member name
 * @param signature member signature text
 * @param changed whether this member was flagged changed by the patch
 * @param callees direct method calls made from this member's body
 * @param callers direct call sites in other members referencing this member
 */
public record MemberRelation(
    String declaringType,
    String memberName,
    String signature,
    boolean changed,
    List<CallSite> callees,
    List<CallSite> callers) {

  public MemberRelation {
    if (declaringType == null || declaringType.isBlank()) {
      throw new DomainException("Declaring type cannot be blank");
    }
    if (memberName == null || memberName.isBlank()) {
      throw new DomainException("Member name cannot be blank");
    }
    signature = signature == null ? "" : signature;
    callees = callees == null ? List.of() : List.copyOf(callees);
    callers = callers == null ? List.of() : List.copyOf(callers);
  }
}
