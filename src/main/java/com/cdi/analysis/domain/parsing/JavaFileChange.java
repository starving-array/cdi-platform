package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

import java.util.List;

/**
 * Structural outcome for one changed file: the Java structure found and the
 * per-member change flags. States follow the existing degrade-don't-crash
 * convention — unparseable/unsupported files yield an explicit state instead
 * of an exception.
 */
public record JavaFileChange(
    String path,
    State state,
    String packageName,
    List<String> imports,
    List<TypeChange> types,
    List<PatchHunk> hunks,
    List<ImportDecl> importDeclarations,
    List<String> referencedTypes) {

  public enum State {
    /** Java source was parsed successfully. */
    PARSED,
    /** A .java file could not be parsed (malformed/generated/unsupported). */
    PARSE_FAILED,
    /** Not a Java file. */
    NON_JAVA,
    /** Java file with empty content. */
    EMPTY,
    /** File was deleted in this change; no source exists at the snapshot. */
    DELETED,
    /** Source content was unavailable (not found / retrieval failed / non-textual). */
    SOURCE_UNAVAILABLE
  }

  public JavaFileChange {
    if (path == null || path.isBlank()) {
      throw new DomainException("Java file path cannot be blank");
    }
    if (state == null) {
      throw new DomainException("Java file state cannot be null");
    }
    packageName = packageName == null ? "" : packageName;
    imports = imports == null ? List.of() : List.copyOf(imports);
    types = types == null ? List.of() : List.copyOf(types);
    hunks = hunks == null ? List.of() : List.copyOf(hunks);
    importDeclarations = importDeclarations == null ? List.of() : List.copyOf(importDeclarations);
    referencedTypes = referencedTypes == null ? List.of() : List.copyOf(referencedTypes);
  }

  /** Backward-compatible constructor (pre-PART 6 shape). */
  public JavaFileChange(String path, State state, String packageName,
                        List<String> imports, List<TypeChange> types, List<PatchHunk> hunks) {
    this(path, state, packageName, imports, types, hunks, List.of(), List.of());
  }

  public static JavaFileChange nonJava(String path) {
    return new JavaFileChange(path, State.NON_JAVA, "", List.of(), List.of(), List.of());
  }

  public static JavaFileChange empty(String path) {
    return new JavaFileChange(path, State.EMPTY, "", List.of(), List.of(), List.of());
  }

  public static JavaFileChange deleted(String path) {
    return new JavaFileChange(path, State.DELETED, "", List.of(), List.of(), List.of());
  }

  public static JavaFileChange sourceUnavailable(String path) {
    return new JavaFileChange(path, State.SOURCE_UNAVAILABLE, "", List.of(), List.of(), List.of());
  }

  public static JavaFileChange parseFailed(String path, List<PatchHunk> hunks) {
    return new JavaFileChange(path, State.PARSE_FAILED, "", List.of(), List.of(), hunks);
  }
}
