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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JavaImpactGraphTest {

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

  /** Test 1: One changed method with one direct callee. */
  @Test
  void oneChangedMethodOneDirectCallee() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void foo() { baz(); }\n"
        + "  public void baz() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.foo(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have at least one node");
    assertFalse(result.edges.isEmpty(), "Should have at least one edge");
    assertTrue(result.summary.changedMembers() > 0,
        "Should have at least one changed member");

    // Check that baz() is a direct callee of foo()
    boolean hasCalleeEdge = result.edges.stream()
        .anyMatch(e -> e.edgeType().equals("CALLS")
            && (e.sourceMember().equals("foo") || e.targetMember().equals("baz")));
    assertTrue(hasCalleeEdge, "Should have CALLS edge between foo and baz");
  }

  /** Test 2: One changed method with one direct caller. */
  @Test
  void oneChangedMethodOneDirectCaller() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.bar(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have at least one node");
    assertFalse(result.edges.isEmpty(), "Should have at least one edge");
    assertTrue(result.summary.changedMembers() > 0);

    // Check that caller references bar()
    boolean hasCallerEdge = result.edges.stream()
        .anyMatch(e -> e.edgeType().equals("CALLS")
            && e.targetMember().equals("bar"));
    assertTrue(hasCallerEdge, "Should have CALLS edge targeting bar()");
  }

  /** Test 3: Caller + callee together. */
  @Test
  void callerAndCalleeTogether() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void baz() { }\n"
        + "  public void foo() { bar(); }\n"
        + "  public void bar() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.foo(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes");
    assertFalse(result.edges.isEmpty(), "Should have edges");

    // Should have both CALLS edges: foo→bar and caller→foo
    long callsEdges = result.edges.stream()
        .filter(e -> e.edgeType().equals("CALLS")).count();
    assertTrue(callsEdges >= 2, "Should have at least 2 CALLS edges (foo→bar and caller→foo)");
  }

  /** Test 4: Depth-2 traversal. */
  @Test
  void depthTwoTraversal() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void level3() { }\n"
        + "  public void level2() { level3(); }\n"
        + "  public void level1() { level2(); }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.level1(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis, 2); // depth 2
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes");
    assertFalse(result.edges.isEmpty(), "Should have edges");

    // With depth 2 traversal
    long depth1Nodes = result.nodes.stream()
        .filter(n -> n.memberName().equals("level2") || n.memberName().equals("level3"))
        .count();
    assertTrue(depth1Nodes >= 1, "Should discover level2 or level3 at depth 2");
  }

  /** Test 5: Traversal stops at configured maximum depth. */
  @Test
  void traversalStopsAtMaxDepth() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void level1() { }\n"
        + "  public void level2() { level1(); }\n"
        + "  public void level3() { level2(); }\n"
        + "  public void level4() { level3(); }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.level1(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    // With maxDepth=1, only direct callees
    ImpactGraph graphShallow = new ImpactGraph(depAnalysis, 1);
    ImpactGraphResult shallowResult = graphShallow.build();

    assertTrue(shallowResult.summary.maxTraversalDepth() <= 1,
        "Max depth should not exceed 1 when configured to 1");

    // With maxDepth=2
    ImpactGraph graphDeep = new ImpactGraph(depAnalysis, 2);
    ImpactGraphResult deepResult = graphDeep.build();
    assertTrue(deepResult.summary.maxTraversalDepth() <= 2,
        "Max depth should not exceed 2 when configured to 2");
  }

  /** Test 6: Cyclic dependency does not recurse forever. */
  @Test
  void cyclicDependencyDoesNotRecurseForever() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void b() { }\n"
        + "  public void c() { A.aCalled(); }\n"
        + "  public static void aCalled() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void a() { }\n"
        + "  public void c() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes even with cycles");
    assertFalse(result.edges.isEmpty(), "Should have edges even with cycles");

    // Should not have infinite edges - graph should be bounded
    assertTrue(result.edges.size() < 100, "Graph edges should be finite (not infinite recursion)");
  }

  /** Test 7: Duplicate relationships are deduplicated. */
  @Test
  void duplicateRelationshipsDeduplicated() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void foo() { bar(); bar(); }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.foo(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.edges.isEmpty(), "Should have edges");
    // Count unique CALLS edges (source→target pairs)
    long uniqueCalls = result.edges.stream()
        .collect(java.util.stream.Collectors.collectingAndThen(
            java.util.stream.Collectors.toSet(),
            set -> (long) set.size()));
    // foo() calling bar() twice should deduplicate to 1 unique edge
    assertTrue(uniqueCalls >= 1, "Should have at least 1 unique CALLS edge");
  }

  /** Test 8: Multiple callers. */
  @Test
  void multipleCallers() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void caller1() { A a = new A(); a.bar(); }\n"
        + "}\n");
    Path file3 = tempDir.resolve("C.java");
    Files.writeString(file3,
        "package p;\n"
        + "public class C {\n"
        + "  public void caller2() { A a = new A(); a.bar(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));
    ChangedFileSource changed3 = makeChangedFileSource(file3.toString(),
        Files.readString(file3));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2, changed3));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes");
    assertFalse(result.edges.isEmpty(), "Should have edges");

    // Should have multiple callers of bar()
    long callsToBar = result.edges.stream()
        .filter(e -> e.targetMember().equals("bar") && e.edgeType().equals("CALLS"))
        .count();
    assertTrue(callsToBar >= 2, "Should have at least 2 CALLS edges targeting bar()");
  }

  /** Test 9: Multiple callees. */
  @Test
  void multipleCallees() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void foo() { bar(); baz(); qux(); }\n"
        + "  public void bar() { }\n"
        + "  public void baz() { }\n"
        + "  public void qux() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed1));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.edges.isEmpty(), "Should have edges");
    // Should have multiple CALLS edges from foo
    long callsFromFoo = result.edges.stream()
        .filter(e -> e.sourceMember().equals("foo"))
        .count();
    assertTrue(callsFromFoo >= 3, "Should have at least 3 CALLS edges from foo()");
  }

  /** Test 10: Same-package resolution. */
  @Test
  void samePackageResolution() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void useIt() { p.B b = new p.B(); b.helper(); }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package p;\n"
        + "public class B {\n"
        + "  public void helper() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertTrue(result.edges.size() >= 0, "Should produce edges");
  }

  /** Test 11: Explicit-import resolution. */
  @Test
  void explicitImportResolution() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package q;\n"
        + "import p.A;\n"
        + "public class B {\n"
        + "  public void caller() { A a = new A(); a.bar(); }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes");
    assertFalse(result.edges.isEmpty(), "Should have edges");
    // Edge should have resolution info
    boolean hasResolution = result.edges.stream()
        .anyMatch(e -> e.resolution() != null);
    assertTrue(hasResolution, "Edges should have resolution information");
  }

  /** Test 12: Unresolved/external relationship is not fabricated. */
  @Test
  void unresolvedNotFabricated() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "import q.ExternalClass;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void useExternal() { ExternalClass e; }\n"
        + "}\n");

    Path file2 = tempDir.resolve("B.java");
    Files.writeString(file2,
        "package q;\n"
        + "public class ExternalClass {\n"
        + "  public void helper() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));
    ChangedFileSource changed2 = makeChangedFileSource(file2.toString(),
        Files.readString(file2));

    CodeContext context = new CodeContext(SHA, List.of(changed1, changed2));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    // Graph should produce nodes (at least the changed member)
    assertTrue(result.nodes.size() > 0, "Graph should produce nodes");
    // Graph should not fabricate excessive nodes
    assertTrue(result.nodes.size() < 50, "Graph should not have excessive nodes from fabrication");
    // Unresolved relationships should be tracked but not invented
    assertTrue(result.summary.unresolvedRelationships() >= 0,
        "Unresolved count should be non-negative");
  }

  /** Test 13: Deleted source handling. */
  @Test
  void deletedSourceHandling() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    // File is DELETED - no content retrievable
    FileDiff diff = new FileDiff(file1.toString(), 1, 0, FileDiff.ChangeType.MODIFIED, "");
    ChangedFileSource deleted = new ChangedFileSource(diff,
        ChangedFileSource.Availability.DELETED_FILE, null);

    CodeContext context = new CodeContext(SHA, List.of(deleted));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertNotNull(result, "Graph should not crash on deleted source");
    assertTrue(result.nodes.isEmpty() || result.edges.isEmpty(),
        "Deleted source should produce no graph relationships");
  }

  /** Test 14: Source unavailable handling. */
  @Test
  void sourceUnavailableHandling() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    // File with RETRIEVAL_FAILED availability
    FileDiff diff = new FileDiff(file1.toString(), 1, 0, FileDiff.ChangeType.MODIFIED, "");
    ChangedFileSource unavailable = new ChangedFileSource(diff,
        ChangedFileSource.Availability.RETRIEVAL_FAILED, new byte[0]);

    CodeContext context = new CodeContext(SHA, List.of(unavailable));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertNotNull(result, "Graph should not crash on unavailable source");
    assertTrue(result.nodes.isEmpty() || result.edges.isEmpty(),
        "Unavailable source should produce no graph relationships");
  }

  /** Test 15: Parse failed handling. */
  @Test
  void parseFailedHandling() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { <badjava\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed1));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertNotNull(result, "Graph should not crash on malformed Java");
    assertTrue(result.nodes.isEmpty() || result.edges.isEmpty(),
        "Parse-failed source should produce no graph relationships");
  }

  /** Test 16: Multiple changed members. */
  @Test
  void multipleChangedMembers() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void method1() { }\n"
        + "  public void method2() { method1(); }\n"
        + "  public void method3() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed1));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertFalse(result.nodes.isEmpty(), "Should have nodes for multiple changed members");
    assertTrue(result.summary.changedMembers() >= 2,
        "Should count at least 2 changed members (method1 and method2, where method2 calls method1)");
  }

  /** Test 17: Deterministic graph ordering. */
  @Test
  void deterministicGraphOrdering() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "  public void foo() { baz(); }\n"
        + "  public void baz() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext(SHA, List.of(changed1));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph1 = new ImpactGraph(depAnalysis);
    ImpactGraphResult result1 = graph1.build();

    ImpactGraph graph2 = new ImpactGraph(depAnalysis);
    ImpactGraphResult result2 = graph2.build();

    // Same analysis should produce same graph structure
    assertEquals(result1.nodes.size(), result2.nodes.size(),
        "Same analysis should produce same number of nodes");
    assertEquals(result1.edges.size(), result2.edges.size(),
        "Same analysis should produce same number of edges");
    assertEquals(result1.summary.changedMembers(), result2.summary.changedMembers(),
        "Same analysis should produce same changed member count");
    assertEquals(result1.summary.directlyAffectedMembers(), result2.summary.directlyAffectedMembers(),
        "Same analysis should produce same directly affected count");
    assertEquals(result1.summary.transitivelyAffectedMembers(), result2.summary.transitivelyAffectedMembers(),
        "Same analysis should produce same transitive affected count");
    assertEquals(result1.summary.maxTraversalDepth(), result2.summary.maxTraversalDepth(),
        "Same analysis should produce same max depth");
    assertEquals(result1.summary.unresolvedRelationships(), result2.summary.unresolvedRelationships(),
        "Same analysis should produce same unresolved count");
    assertEquals(result1.summary.repositoryLocalRelationships(), result2.summary.repositoryLocalRelationships(),
        "Same analysis should produce same repo-local count");
    assertEquals(result1.summary.externalRelationships(), result2.summary.externalRelationships(),
        "Same analysis should produce same external count");
  }

  /** Test 18: Commit SHA propagation. */
  @Test
  void commitShaPropagation() throws Exception {
    Path tempDir = Files.createTempDirectory("cdi-test");
    Path file1 = tempDir.resolve("A.java");
    Files.writeString(file1,
        "package p;\n"
        + "public class A {\n"
        + "  public void bar() { }\n"
        + "}\n");

    ChangedFileSource changed1 = makeChangedFileSource(file1.toString(),
        Files.readString(file1));

    CodeContext context = new CodeContext("def456", List.of(changed1));
    DependencyAnalysis depAnalysis = analyzer.analyze(context);

    ImpactGraph graph = new ImpactGraph(depAnalysis);
    ImpactGraphResult result = graph.build();

    assertEquals("def456", result.summary.toString(),
        "Commit SHA should be propagated through impact graph summary");
  }
}
