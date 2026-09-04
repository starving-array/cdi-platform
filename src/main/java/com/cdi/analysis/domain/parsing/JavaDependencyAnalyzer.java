package com.cdi.analysis.domain.parsing;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.*;
import java.util.stream.Collectors;

public final class JavaDependencyAnalyzer {

  private final JavaParser parser =
      new JavaParser(new ParserConfiguration()
          .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));

  public DependencyAnalysis analyze(CodeContext context) {
    List<ParsedFile> parsed = new ArrayList<>();
    for (ChangedFileSource file : context.changedFiles()) {
      if (file.availability() != ChangedFileSource.Availability.RETRIEVED
          || !file.path().endsWith(".java")) {
        continue;
      }
      byte[] bytes = file.contentOptional().orElse(new byte[0]);
      if (bytes.length == 0) {
        continue;
      }
      try {
        var result = parser.parse(new String(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        result.getResult().ifPresent(cu -> parsed.add(new ParsedFile(file.path(), cu,
            cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse(""))));
      } catch (RuntimeException ignored) {
        // Malformed source degrades (empty contribution), matching PART 5.
      }
    }

    Map<String, String> declared = new HashMap<>();
    for (ParsedFile pf : parsed) {
      for (TypeDeclaration<?> type : pf.cu.getTypes()) {
        for (TypeDeclaration<?> t : collectAll(type)) {
          declared.putIfAbsent(t.getNameAsString(), qualify(pf.packageName, t));
        }
      }
    }

    List<FileDependencies> out = new ArrayList<>();
    for (ParsedFile pf : parsed) {
      out.add(analyzeFile(pf, declared, parsed));
    }
    return new DependencyAnalysis(context.commitSha(), List.copyOf(out));
  }

  private static List<TypeDeclaration<?>> collectAll(TypeDeclaration<?> root) {
    List<TypeDeclaration<?>> all = new ArrayList<>();
    all.add(root);
    root.findAll(TypeDeclaration.class).forEach(t -> {
      if (t != root) all.add(t);
    });
    return all;
  }

  private static String qualify(String packageName, TypeDeclaration<?> type) {
    List<String> names = new ArrayList<>();
    TypeDeclaration<?> cur = type;
    while (cur != null) {
      names.add(0, cur.getNameAsString());
      cur = cur.getParentNode().filter(TypeDeclaration.class::isInstance)
          .map(p -> (TypeDeclaration<?>) p).orElse(null);
    }
    String nested = String.join(".", names);
    return packageName == null || packageName.isBlank() ? nested : packageName + "." + nested;
  }

  private FileDependencies analyzeFile(ParsedFile pf, Map<String, String> declaredInContext,
                                       List<ParsedFile> allParsed) {
    Map<String, String> simpleToQualified = new HashMap<>();
    for (var imp : pf.cu.getImports()) {
      if (imp.isAsterisk()) {
        continue;
      }
      String q = imp.getNameAsString();
      String simple = q.substring(q.lastIndexOf('.') + 1);
      simpleToQualified.put(simple, q);
    }
    for (TypeDeclaration<?> type : pf.cu.getTypes()) {
      for (TypeDeclaration<?> t : collectAll(type)) {
        simpleToQualified.putIfAbsent(t.getNameAsString(), qualify(pf.packageName, t));
      }
    }

    List<ResolvedTypeRef> refs = resolveReferences(pf, declaredInContext, simpleToQualified);

    List<MemberRelation> members = new ArrayList<>();
    for (TypeDeclaration<?> type : pf.cu.getTypes()) {
      collectMemberRelations(pf, type, simpleToQualified, declaredInContext, allParsed, members);
    }
    return new FileDependencies(pf.path,
        pf.cu.getTypes().stream()
            .flatMap(t -> collectAll(t).stream().map(x -> qualify(pf.packageName, x)))
            .toList(), refs, List.copyOf(members));
  }

  private List<ResolvedTypeRef> resolveReferences(ParsedFile pf, Map<String, String> declaredInContext,
                                                  Map<String, String> simpleToQualified) {
    List<String> referencedTypeNames = pf.cu.findAll(ClassOrInterfaceType.class).stream()
        .map(t -> t.getNameWithScope())
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream().toList();

    List<ResolvedTypeRef> out = new ArrayList<>();
    for (String raw : referencedTypeNames) {
      out.add(resolveOne(raw, declaredInContext, simpleToQualified, pf.packageName));
    }
    return List.copyOf(out);
  }

  private ResolvedTypeRef resolveOne(String raw, Map<String, String> declaredInContext,
                                     Map<String, String> simpleToQualified, String packageName) {
    String simple = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;

    if (raw.contains(".")) {
      if (declaredInContext.containsValue(raw)) {
        return new ResolvedTypeRef(simple, raw, Resolution.REPOSITORY);
      }
      return new ResolvedTypeRef(simple, raw, Resolution.EXTERNAL);
    }
    String qualified = simpleToQualified.get(simple);
    if (qualified != null && !qualified.equals(simple)) {
      if (declaredInContext.containsValue(qualified) || isSamePackage(qualified, packageName)) {
        return new ResolvedTypeRef(simple, qualified, Resolution.REPOSITORY);
      }
      return new ResolvedTypeRef(simple, qualified, Resolution.EXTERNAL);
    }
    if (declaredInContext.containsKey(simple)) {
      String q = declaredInContext.get(simple);
      if (isSamePackage(q, packageName)) {
        return new ResolvedTypeRef(simple, q, Resolution.REPOSITORY);
      }
      return new ResolvedTypeRef(simple, q, Resolution.EXTERNAL);
    }
    return new ResolvedTypeRef(simple, simple, Resolution.UNRESOLVED);
  }

  private boolean isSamePackage(String qualified, String packageName) {
    if (qualified == null || packageName == null || packageName.isBlank()) {
      return false;
    }
    int lastDot = qualified.lastIndexOf('.');
    return lastDot > 0 && qualified.substring(0, lastDot).equals(packageName);
  }

  /** Walks a type (and nested types) and emits one MemberRelation per member. */
  private void collectMemberRelations(ParsedFile pf, TypeDeclaration<?> type,
                                      Map<String, String> simpleToQualified,
                                      Map<String, String> declaredInContext,
                                      List<ParsedFile> allParsed,
                                      List<MemberRelation> out) {
    String ownerQualified = qualify(pf.packageName, type);

    for (MethodDeclaration m : type.getMethods()) {
      out.add(memberRelation(pf, type, ownerQualified, m.getNameAsString(),
          m.getDeclarationAsString(true, true, true), isChanged(pf, type, m),
          m.getBody().map(b -> b.findAll(MethodCallExpr.class)).orElse(List.of()),
          simpleToQualified, declaredInContext, allParsed));
    }
    for (ConstructorDeclaration c : type.getConstructors()) {
      out.add(memberRelation(pf, type, ownerQualified, c.getNameAsString(),
          c.getDeclarationAsString(true, true, true), isChanged(pf, type, c),
          c.getBody().findAll(MethodCallExpr.class),
          simpleToQualified, declaredInContext, allParsed));
    }

    for (var member : type.getMembers()) {
      if (member instanceof TypeDeclaration<?> nested) {
        collectMemberRelations(pf, nested, simpleToQualified, declaredInContext, allParsed, out);
      }
    }
  }

  private MemberRelation memberRelation(ParsedFile pf, TypeDeclaration<?> type, String ownerQualified,
                                        String memberName, String signature, boolean changed,
                                        List<MethodCallExpr> bodyCalls,
                                        Map<String, String> simpleToQualified,
                                        Map<String, String> declaredInContext,
                                        List<ParsedFile> allParsed) {

    // --- callees: calls made INSIDE this member's body ---
    List<CallSite> callees = new ArrayList<>();
    Map<String, String> locals = localScopeTypes(type, simpleToQualified, declaredInContext);
    for (MethodCallExpr call : bodyCalls) {
      callees.add(resolveCallTarget(call, memberName, ownerQualified, type, pf,
          locals, simpleToQualified, declaredInContext));
    }

    // --- callers: other member bodies that call this member, across the context ---
    List<CallSite> callers = new ArrayList<>();
    for (ParsedFile other : allParsed) {
      for (MethodCallExpr call : other.cu.findAll(MethodCallExpr.class)) {
        if (!call.getNameAsString().equals(memberName)) {
          continue;
        }
        var enclosingMethod = call.findAncestor(MethodDeclaration.class);
        var enclosingCtor = call.findAncestor(ConstructorDeclaration.class);
        var enclosingTypeNode = call.findAncestor(TypeDeclaration.class);
        if (enclosingTypeNode.isEmpty()) continue;
        String enclosingOwner = qualify(other.packageName, enclosingTypeNode.get());
        String enclosingMemberName =
            enclosingMethod.map(MethodDeclaration::getNameAsString)
                .orElse(enclosingCtor.map(ConstructorDeclaration::getNameAsString).orElse(""));

        if (enclosingTypeNode.get() == type && enclosingMemberName.equals(memberName)) {
          continue;
        }

        String resolvedOwner = resolveOwner(call, enclosingOwner, enclosingTypeNode.get(),
            localScopeTypes(enclosingTypeNode.get(), simpleToQualified, declaredInContext),
            other, simpleToQualified, declaredInContext, type);

        boolean overloadAmbiguity = type.getMethodsByName(memberName).size() > 1;

        if (resolvedOwner != null) {
          Resolution callerRes = resolvedOwner.equals(ownerQualified)
              ? Resolution.REPOSITORY : Resolution.EXTERNAL;
          callers.add(new CallSite(enclosingOwner, memberName,
              callerRes, overloadAmbiguity, other.path, enclosingOwner, enclosingMemberName));
        } else if (resolvedOwner == null) {
          callers.add(new CallSite(enclosingOwner, memberName, Resolution.UNRESOLVED,
              true, other.path, enclosingOwner, enclosingMemberName));
        }
        // Owner resolved to a different type → not a caller.
      }
    }

    return new MemberRelation(ownerQualified, memberName, signature, changed,
        List.copyOf(callees), List.copyOf(callers));
  }

private boolean isChanged(ParsedFile pf, TypeDeclaration<?> type,
                          com.github.javaparser.ast.body.CallableDeclaration<?> member) {
    return true;
  }

  private String resolveOwner(MethodCallExpr call, String enclosingOwner,
                              TypeDeclaration<?> enclosingType,
                              Map<String, String> locals,
                              ParsedFile other,
                              Map<String, String> simpleToQualified,
                              Map<String, String> declaredInContext,
                              TypeDeclaration<?> declaringType) {
    String method = call.getNameAsString();
    Optional<Expression> scope = call.getScope();

    if (scope.isEmpty() || scope.get() instanceof ThisExpr) {
      boolean ownerHasMethod = declaringType.getMethodsByName(method).size() > 0
          || declaringType.getConstructors().stream()
              .anyMatch(c -> c.getNameAsString().equals(method));
      return ownerHasMethod ? enclosingOwner : null;
    }

    Expression scopeExpr = scope.get();
    String receiverType;

    if (scopeExpr instanceof NameExpr n) {
      receiverType = resolveTypeName(n.getNameAsString(), simpleToQualified, declaredInContext, other.packageName);
    } else if (scopeExpr instanceof FieldAccessExpr f) {
      receiverType = resolveReceiverType(f, simpleToQualified, declaredInContext, other.packageName);
    } else {
      return null;
    }

    if (receiverType != null) {
      return receiverType;
    }
    return null;
  }

  private String resolveTypeName(String name, Map<String, String> simpleToQualified,
                                 Map<String, String> declaredInContext, String packageName) {
    if (name.contains(".")) return name;
    String viaImport = simpleToQualified.get(name);
    if (viaImport != null) return viaImport;
    if (declaredInContext.containsKey(name)) {
      return declaredInContext.get(name);
    }
    return packageName == null || packageName.isBlank() ? name : packageName + "." + name;
  }

  private String resolveReceiverType(FieldAccessExpr f,
                                     Map<String, String> simpleToQualified,
                                     Map<String, String> declaredInContext,
                                     String packageName) {
    Expression scope = f.getScope();
    String receiver;
    if (scope instanceof ThisExpr) {
      receiver = "this";
    } else if (scope instanceof NameExpr n) {
      receiver = resolveTypeName(n.getNameAsString(), simpleToQualified, declaredInContext, packageName);
    } else {
      receiver = null;
    }
    return receiver;
  }

  private CallSite resolveCallTarget(MethodCallExpr call, String contextMember,
                                     String declaringOwnerQualified, TypeDeclaration<?> declaringType,
                                     ParsedFile pf,
                                     Map<String, String> locals,
                                     Map<String, String> simpleToQualified,
                                     Map<String, String> declaredInContext) {
    String method = call.getNameAsString();
    Optional<Expression> scope = call.getScope();

    if (scope.isEmpty() || scope.get() instanceof ThisExpr) {
      boolean ownerHasMethod = declaringType.getMethodsByName(method).size() > 0
          || declaringType.getConstructors().stream()
              .anyMatch(c -> c.getNameAsString().equals(method));
      return new CallSite(declaringOwnerQualified, method,
          ownerHasMethod ? Resolution.REPOSITORY : Resolution.UNRESOLVED,
          declaringType.getMethodsByName(method).size() > 1, pf.path, declaringOwnerQualified, contextMember);
    }

    Expression scopeExpr = scope.get();
    String scopeName;
    if (scopeExpr instanceof NameExpr n) {
      scopeName = n.getNameAsString();
    } else if (scopeExpr instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr) {
      scopeName = f.getNameAsString();
    } else {
      return new CallSite("", method, Resolution.UNRESOLVED, true, "unknown", declaringOwnerQualified, contextMember);
    }

    // 1. receiver = another field/variable/parameter in this type
    String varType = locals.get(scopeName);
    if (varType != null) {
      String q = qualifiedNameOf(varType, simpleToQualified, pf.packageName, declaredInContext);
      Resolution r = resolutionFor(q, declaredInContext, pf.packageName);
      boolean ambiguous = declaredInContext.containsValue(q) ? false : true;
      return new CallSite(q, method, r, ambiguous, "unknown", declaringOwnerQualified, contextMember);
    }

    // 2. receiver = type (static-style or FQ use)
    String q = qualifiedNameOf(scopeName, simpleToQualified, pf.packageName, declaredInContext);
    if (q != null && !q.equals(scopeName)) {
      return new CallSite(q, method, resolutionFor(q, declaredInContext, pf.packageName),
          false, "unknown", declaringOwnerQualified, contextMember);
    }
    return new CallSite("", method, Resolution.UNRESOLVED, true, "unknown", declaringOwnerQualified, contextMember);
  }

  private static Map<String, String> localScopeTypes(TypeDeclaration<?> type,
                                                     Map<String, String> simpleToQualified,
                                                     Map<String, String> declaredInContext) {
    Map<String, String> out = new HashMap<>();
    for (var field : type.getFields()) {
      String ft = field.getElementType().isClassOrInterfaceType()
          ? field.getElementType().asClassOrInterfaceType().getNameWithScope()
          : null;
      if (ft != null) {
        for (VariableDeclarator v : field.getVariables()) {
          out.put(v.getNameAsString(), ft);
        }
      }
    }
    for (MethodDeclaration m : type.getMethods()) {
      for (Parameter p : m.getParameters()) {
        if (p.getType().isClassOrInterfaceType()) {
          out.put(p.getNameAsString(), p.getType().asClassOrInterfaceType().getNameWithScope());
        }
      }
    }
    for (ConstructorDeclaration c : type.getConstructors()) {
      for (Parameter p : c.getParameters()) {
        if (p.getType().isClassOrInterfaceType()) {
          out.put(p.getNameAsString(), p.getType().asClassOrInterfaceType().getNameWithScope());
        }
      }
    }
    return out;
  }

  private String qualifiedNameOf(String name, Map<String, String> simpleToQualified,
                                 String packageName, Map<String, String> declaredInContext) {
    if (name.contains(".")) return name;
    String viaImport = simpleToQualified.get(name);
    if (viaImport != null) return viaImport;
    if (declaredInContext.containsKey(name)) {
      return declaredInContext.get(name);
    }
    return packageName == null || packageName.isBlank() ? name : packageName + "." + name;
  }

  private Resolution resolutionFor(String qualified, Map<String, String> declaredInContext, String packageName) {
    if (qualified == null || qualified.isBlank()) return Resolution.UNRESOLVED;
    if (declaredInContext.containsValue(qualified)) return Resolution.REPOSITORY;
    if (isSamePackage(qualified, packageName) && declaredInContext.containsKey(
        qualified.substring(qualified.lastIndexOf('.') + 1))) return Resolution.REPOSITORY;
    return qualified.contains(".") ? Resolution.EXTERNAL : Resolution.UNRESOLVED;
  }

  private record ResolvedTargetCall(String ownerQualified) {}

  /** One parsed Java source file plus its package at analysis time. */
  private record ParsedFile(String path, CompilationUnit cu, String packageName) {}
}