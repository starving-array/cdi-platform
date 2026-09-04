package com.cdi.analysis.domain.parsing;

/**
 * One Java member (method or constructor) and whether this change touched it.
 *
 * <p>{@code changed} is best-effort: derived from patch-hunk line-range
 * intersection (see {@code JavaChangeAnalyzer}). It is a conservative signal —
 * shared-level precision, not full AST diffing.
 */
public record MemberChange(String name, String signature, int beginLine, int endLine, boolean changed) {
}
