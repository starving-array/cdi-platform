package com.cdi.analysis.domain.parsing;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.FileDiff;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Java structural analysis over a {@link CodeContext}.
 *
 * <p>Parses each Java file with available source and answers: which types and
 * members exist, and which of them the patch hunks touch. Patch → AST mapping
 * is <em>hunk-line-intersection</em> on the NEW-side line numbers of the
 * post-change source: a member is {@code changed} iff its line range overlaps
 * any new-side hunk range. This is a conservative range-level heuristic — see
 * the limitations documented in {@code JavaFileChange}.
 *
 * <p>Failure model follows the existing pipeline convention: a single
 * unparseable/unsupported file degrades to a typed {@link
 * JavaFileChange.State}; it never aborts the whole analysis, and content from
 * other commits is never substituted.
 */
public final class JavaChangeAnalyzer {

  private final JavaParser parser =
      new JavaParser(new ParserConfiguration()
          .setCharacterEncoding(StandardCharsets.UTF_8)
          .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));

  /**
   * Analyzes the given code context. The returned analysis carries the exact
   * same commit SHA as the context; no ref substitution ever happens.
   */
  public JavaChangeAnalysis analyze(CodeContext context) {
    List<JavaFileChange> files = new ArrayList<>();
    for (ChangedFileSource file : context.changedFiles()) {
      files.add(analyzeFile(file));
    }
    return new JavaChangeAnalysis(context.commitSha(), files);
  }

  private JavaFileChange analyzeFile(ChangedFileSource file) {
    FileDiff.ChangeType changeType = file.diff().changeType();
    if (changeType == FileDiff.ChangeType.DELETED) {
      return JavaFileChange.deleted(file.path());
    }
    if (file.availability() != ChangedFileSource.Availability.RETRIEVED) {
      return JavaFileChange.sourceUnavailable(file.path());
    }
    if (!file.path().endsWith(".java")) {
      return JavaFileChange.nonJava(file.path());
    }

    byte[] bytes = file.contentOptional().orElse(new byte[0]);
    if (bytes.length == 0) {
      return JavaFileChange.empty(file.path());
    }

    List<PatchHunk> hunks = PatchHunk.parse(file.diff().patch());

    final CompilationUnit cu;
    try {
      var result = parser.parse(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)));
      if (!result.isSuccessful() || result.getResult().isEmpty()) {
        return JavaFileChange.parseFailed(file.path(), hunks);
      }
      cu = result.getResult().get();
    } catch (RuntimeException e) {
      // Parser-level blowup (StackOverflow/OOM on pathological input is caught
      // upstream; here a generic parse failure just degrades the file).
      return JavaFileChange.parseFailed(file.path(), hunks);
    }

    String packageName = cu.getPackageDeclaration()
        .map(p -> p.getNameAsString())
        .orElse("");
    List<String> imports = cu.getImports().stream()
        .map(i -> i.getNameAsString() + (i.isAsterisk() ? ".*" : ""))
        .toList();
    List<ImportDecl> importDecls = cu.getImports().stream()
        .map(i -> new ImportDecl(i.getNameAsString(), i.isStatic(), i.isAsterisk()))
        .toList();

    boolean added = changeType == FileDiff.ChangeType.ADDED;
    List<TypeChange> types = new ArrayList<>();
    for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
      types.add(toTypeChange(type, hunks, added));
    }

    List<String> referencedTypes = cu.findAll(
        com.github.javaparser.ast.type.ClassOrInterfaceType.class).stream()
        .map(com.github.javaparser.ast.type.ClassOrInterfaceType::getNameWithScope)
        .collect(java.util.stream.Collectors.collectingAndThen(
            java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new),
            List::copyOf));

    return new JavaFileChange(file.path(), JavaFileChange.State.PARSED,
        packageName, imports, List.copyOf(types), hunks, importDecls, referencedTypes);
  }

  private TypeChange toTypeChange(TypeDeclaration<?> type, List<PatchHunk> hunks, boolean added) {
    TypeChange.Kind kind = toKind(type);
    boolean nested = type.getParentNode().filter(p -> p instanceof TypeDeclaration).isPresent();
    int begin = type.getBegin().map(p -> p.line).orElse(-1);
    int end = type.getEnd().map(p -> p.line).orElse(-1);

    List<MemberChange> methods = new ArrayList<>();
    for (MethodDeclaration m : type.getMethods()) {
      methods.add(toMember(m.getNameAsString(), m.getDeclarationAsString(true, true, true),
          line(m.getBegin()), line(m.getEnd()), hunks, added));
    }
    List<MemberChange> ctors = new ArrayList<>();
    for (ConstructorDeclaration c : type.getConstructors()) {
      ctors.add(toMember(c.getNameAsString(), c.getDeclarationAsString(true, true, true),
          line(c.getBegin()), line(c.getEnd()), hunks, added));
    }
    List<String> fields = type.getFields().stream()
        .flatMap(f -> f.getVariables().stream().map(v -> v.getNameAsString()))
        .toList();

    boolean changed = added || rangeIntersectsHunks(begin, end, hunks)
        || methods.stream().anyMatch(MemberChange::changed)
        || ctors.stream().anyMatch(MemberChange::changed);

    return new TypeChange(type.getNameAsString(), kind, nested, begin, end, changed,
        List.of(), fields, List.copyOf(methods), List.copyOf(ctors));
  }

  private MemberChange toMember(String name, String signature, int begin, int end,
                                List<PatchHunk> hunks, boolean added) {
    boolean changed = added || rangeIntersectsHunks(begin, end, hunks);
    return new MemberChange(name, signature, begin, end, changed);
  }

  /**
   * Range intersection between a member's source lines and the NEW-side lines
   * of the patch hunks. A zero-length hunk (pure deletion between lines X and
   * X+1) is treated as touching line {@code newStart} so members immediately
   * surrounding a deletion are flagged conservatively.
   */
  private static boolean rangeIntersectsHunks(int begin, int end, List<PatchHunk> hunks) {
    if (begin < 0 || end < 0) {
      return false;
    }
    for (PatchHunk h : hunks) {
      int hStart = h.newStart();
      int hEnd = h.newLength() > 0 ? h.newStart() + h.newLength() - 1 : h.newStart();
      if (begin <= hEnd && hStart <= end) {
        return true;
      }
    }
    return false;
  }

  private static TypeChange.Kind toKind(TypeDeclaration<?> type) {
    if (type instanceof com.github.javaparser.ast.body.EnumDeclaration) return TypeChange.Kind.ENUM;
    if (type instanceof com.github.javaparser.ast.body.RecordDeclaration) return TypeChange.Kind.RECORD;
    if (type instanceof com.github.javaparser.ast.body.AnnotationDeclaration) return TypeChange.Kind.ANNOTATION;
    if (type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration coid) {
      return coid.isInterface() ? TypeChange.Kind.INTERFACE : TypeChange.Kind.CLASS;
    }
    return TypeChange.Kind.CLASS;
  }

  private static int line(java.util.Optional<com.github.javaparser.Position> pos) {
    return pos.map(p -> p.line).orElse(-1);
  }
}
