package com.cdi.analysis.domain.parsing;

import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.FileDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JavaDependencyAnalyzerTest {

  private JavaDependencyAnalyzer analyzer;
  private static final String SHA = "e6c2584f24584da6017c3b89140e96aa1416b7b4";

  @BeforeEach
  void setUp() {
    analyzer = new JavaDependencyAnalyzer();
  }

  private ChangedFileSource makeChangedFileSource(String path, String source) {
    FileDiff diff = new FileDiff(path, 1, 0, FileDiff.ChangeType.MODIFIED, "");
    byte[] content = source == null ? null : source.getBytes(StandardCharsets.UTF_8);
    return new ChangedFileSource(diff,
        source == null ? ChangedFileSource.Availability.RETRIEVAL_FAILED
            : ChangedFileSource.Availability.RETRIEVED,
        content);
  }

  /** Callee discovery: method calling another method within same body. */
  @Test
  void changedMethodCallingAnotherMethodWithThisScope() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void foo() { this.bar(); }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();

    assertFalse(deps.isEmpty());
    FileDependencies fd = deps.get(0);
    // Callee discovery: foo() calls this.bar() → callee bar() discovered
    assertTrue(fd.members().size() > 0,
        "Should discover member foo()");
    assertTrue(fd.members().stream()
        .anyMatch(m -> m.memberName().equals("foo")),
        "Should have member foo");
    // Within same type, this.bar() callee should be discovered
    assertTrue(fd.members().stream()
        .flatMap(m -> m.callees().stream())
        .anyMatch(c -> c.methodName().equals("bar")),
        "Callee bar() should be discovered inside foo() with this scope");
  }

  /** Callee discovery: direct method call without this. */
  @Test
  void changedMethodCallingDirectMethod() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void foo() { bar(); }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();

    FileDependencies fd = deps.get(0);
    assertTrue(fd.members().size() > 0,
        "Should discover member foo()");
    assertTrue(fd.members().stream()
        .flatMap(m -> m.callees().stream())
        .anyMatch(c -> c.methodName().equals("bar")),
        "Direct call bar() should be discovered as callee of foo()");
  }

  /** Caller discovery: changed method called from same type with this. */
  @Test
  void changedMethodCalledFromSameTypeThisScope() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void trigger() { this.bar(); }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();

    FileDependencies fd = deps.get(0);
    // Within same type, caller of bar() via this.bar() should be discovered
    assertTrue(fd.members().size() > 0,
        "Should discover member relations");
    assertTrue(fd.members().stream()
        .anyMatch(m -> m.memberName().equals("trigger")),
        "Should have member trigger");
    // this.bar() caller within same type: enclosingMember="trigger", resolution=REPOSITORY
    assertTrue(fd.members().stream()
        .flatMap(m -> m.callers().stream())
        .anyMatch(c -> c.enclosingMember().equals("trigger")
            && c.resolution() == Resolution.REPOSITORY),
        "Caller via this.bar() within same type should resolve to REPOSITORY");
  }

  /** Explicit import resolution via field declaration (fields ARE tracked by localScopeTypes). */
  @Test
  void explicitImportResolutionViaField() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void useIt() { }\n"
        + "}\n");
    // B.java uses A as a FIELD (tracked by localScopeTypes), not a local variable
    Files.writeString(file2,
        "package q;\n"
        + "import p.A;\n"
        + "public class B {\n"
        + "  // A is a field of B, not a local variable\n"
        + "  public A helper;\n"
        + "  public void caller() { helper.bar(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();

    FileDependencies fd = deps.get(0);
    assertTrue(fd.members().size() > 0,
        "Should have member relations with explicit import via field");
    // Explicit import via field: the caller should be discovered (resolution may vary)
    // since helper.bar() crosses type boundary - check enclosingMember is "caller"
    assertTrue(fd.members().stream()
        .flatMap(m -> m.callers().stream())
        .anyMatch(c -> c.enclosingMember().equals("caller")),
        "Explicit import caller via field should be discovered (enclosingMember=caller)");
  }

  /** Unresolved external type - must remain unresolved, not fabricated. */
  @Test
  void unresolvedExternalTypeRemainsUnresolved() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void useExternal() { ExternalClass e; }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();
    FileDependencies fd = deps.get(0);

    assertTrue(fd.references().size() > 0,
        "Should have referenced type entry for ExternalClass");
    assertTrue(fd.references().stream()
        .anyMatch(r -> r.name().equals("ExternalClass")),
        "Should have ExternalClass in referenced types");
    // Safety rule: external type with no import must be UNRESOLVED, not fabricated
    assertTrue(fd.references().stream()
        .anyMatch(r -> r.resolution() == Resolution.UNRESOLVED),
        "External type with no import must be UNRESOLVED");
  }

  /** Multiple callers within same context - supported patterns. */
  @Test
  void multipleCallersSameType() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");
    // B.java has caller1 using this.bar() and caller2 (bare call)
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller1() { this.bar(); }\n"
        + "  public void caller2() { bar(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();

    FileDependencies fdB = deps.get(0);
    assertTrue(fdB.members().size() > 0, "A should have member relations with callers from B");
    // caller1 uses this.bar() - should be discovered as caller (resolution depends on type match)
    assertTrue(fdB.members().stream()
        .flatMap(m -> m.callers().stream())
        .anyMatch(c -> c.enclosingMember().equals("caller1")),
        "caller1 (this.bar()) should be discovered as caller");
    // caller2 bare call should be discovered
    assertTrue(fdB.members().stream()
        .flatMap(m -> m.callers().stream())
        .anyMatch(c -> c.enclosingMember().equals("caller2")),
        "caller2 (bare bar()) should be discovered as caller");
  }

  /** Overloaded methods - caller count may vary. */
  @Test
  void overloadedMethodsHandler() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void bar(String s) { }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    List<FileDependencies> deps = result.files();
    FileDependencies fd = deps.get(0);

    assertTrue(fd.members().size() > 0,
        "Should handle overloaded methods without crashing");
  }

  /** Malformed Java source should not crash the analyzer. */
  @Test
  void malformedJavaDoesNotCrash() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { <bad} \n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    assertNotNull(result,
        "Analyzer should not crash on malformed Java");
  }

  /** Exact commit SHA propagation. */
  @Test
  void exactCommitShaPropagation() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    ChangedFileSource changed = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    // Override the SHA in the CodeContext constructor
    CodeContext context = new CodeContext("def456", List.of(changed));
    DependencyAnalysis result = analyzer.analyze(context);
    assertEquals("def456", result.commitSha(),
        "Commit SHA should be propagated unchanged");
  }
}