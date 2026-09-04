package com.cdi.analysis.domain.parsing;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.FileDiff;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PART 5: Java structural analysis over a CodeContext. Fixture-based —
 * sources are inline strings bound to a fixed analysis SHA.
 */
class JavaChangeAnalyzerTest {

  private static final String SHA = "e6c2584f24584da6017c3b89140e96aa1416b7b4";

  private final JavaChangeAnalyzer analyzer = new JavaChangeAnalyzer();

  private CodeContext ctx(FileDiff diff, String source) {
    ChangedFileSource file = new ChangedFileSource(diff,
        source == null ? ChangedFileSource.Availability.NOT_FOUND
            : ChangedFileSource.Availability.RETRIEVED,
        source == null ? null : source.getBytes(StandardCharsets.UTF_8));
    return new CodeContext(SHA, List.of(file));
  }

  @Test
  void parsesNormalClass_detectsNamePackageImportsFieldsMethods() {
    String src = """
        package com.demo.pay;
        import java.util.List;
        import com.demo.fraud.FraudService;
        public class PaymentService {
            private final FraudService fraudService;
            private int retries;
            public PaymentService(FraudService fraudService) { this.fraudService = fraudService; }
            public void authorize(int amount) { fraudService.validate(); }
        }
        """;

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("src/main/java/com/demo/pay/PaymentService.java", 5, 1,
            FileDiff.ChangeType.MODIFIED, ""), src));

    assertEquals(SHA, result.commitSha());
    JavaFileChange file = result.files().get(0);
    assertEquals(JavaFileChange.State.PARSED, file.state());
    assertEquals("com.demo.pay", file.packageName());
    assertTrue(file.imports().contains("java.util.List"));
    assertTrue(file.imports().contains("com.demo.fraud.FraudService"));

    assertEquals(1, file.types().size());
    TypeChange type = file.types().get(0);
    assertEquals("PaymentService", type.name());
    assertEquals(TypeChange.Kind.CLASS, type.kind());
    assertFalse(type.nested());

    // fields detected
    assertTrue(type.fields().contains("fraudService"));
    assertTrue(type.fields().contains("retries"));

    // methods + constructor detected
    assertEquals(List.of("authorize"),
        type.methods().stream().map(MemberChange::name).toList());
    assertEquals(List.of("PaymentService"),
        type.constructors().stream().map(MemberChange::name).toList());
    assertTrue(type.methods().get(0).signature().contains("void authorize(int amount)"));
  }

  @Test
  void detectsMultipleTopLevelAndNestedTypes() {
    String src = """
        package x;
        public class Outer {
            static class Inner { void innerMethod() {} }
            interface I { void go(); }
        }
        enum Mode { A, B }
        interface External { void run(); }
        """;

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("x.java", 1, 0, FileDiff.ChangeType.MODIFIED, ""), src));

    JavaFileChange file = result.files().get(0);
    // Outer + nested Inner + nested interface I + top-level enum Mode + top-level interface External
    assertEquals(5, file.types().size());
    assertTrue(file.types().stream().anyMatch(t -> t.name().equals("Outer") && !t.nested()));
    assertTrue(file.types().stream().anyMatch(t -> t.name().equals("Inner") && t.nested()));
    assertTrue(file.types().stream().anyMatch(t -> t.name().equals("I") && t.nested()
        && t.kind() == TypeChange.Kind.INTERFACE));
    assertTrue(file.types().stream().anyMatch(t -> t.name().equals("Mode")
        && t.kind() == TypeChange.Kind.ENUM));
    assertTrue(file.types().stream().anyMatch(t -> t.name().equals("External")
        && t.kind() == TypeChange.Kind.INTERFACE && !t.nested()));
  }

  @Test
  void detectsChangedMethodFromRepresentativePatch() {
    String src = """
        package m;
        class Account {
            int balance;
            void deposit(int amt) { balance += amt; }
            boolean withdraw(int amt) {
                if (balance < amt) return false;
                balance -= amt;
                return true;
            }
        }
        """;
    // Patch: only lines 8 (balance -= amt) changed inside withdraw: old line 8 -> new line 8.
    String patch = "@@ -7,3 +7,3 @@\n if (balance < amt) return false;\n-balance -= amt;\n+balance -= amt; // guard\n}\n";

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Account.java", 1, 1, FileDiff.ChangeType.MODIFIED, patch), src));

    TypeChange type = result.files().get(0).types().get(0);
    MemberChange deposit = type.methods().stream().filter(m -> m.name().equals("deposit")).findFirst().orElseThrow();
    MemberChange withdraw = type.methods().stream().filter(m -> m.name().equals("withdraw")).findFirst().orElseThrow();

    assertFalse(deposit.changed(), "untouched method should not be flagged");
    assertTrue(withdraw.changed(), "hunk inside withdraw must flag it");
    assertTrue(type.changed());
  }

  @Test
  void detectsMultipleChangedMethods() {
    String src = """
        class Pair {
            void a() { int x = 1; }
            void b() { int y = 2; }
            void c() { int z = 3; }
            void d() { int w = 4; }
        }
        """;
    // Two hunks: line 3 (b) and line 5 (d), one line each
    String patch = "@@ -3,1 +3,1 @@\n-b1\n+b1mod\n@@ -5,1 +5,1 @@\n-d1\n+d1mod";

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Pair.java", 2, 2, FileDiff.ChangeType.MODIFIED, patch), src));

    List<Boolean> flagged = result.files().get(0).types().get(0).methods().stream()
        .map(MemberChange::changed).toList();
    assertEquals(List.of(false, true, false, true), flagged, "a(2), b(3), c(4), d(5) — hunks touch only b and d");
  }

  @Test
  void addedJavaFileMarksAllMembersChanged() {
    String src = """
        class Fresh {
            void first() {}
            void second() {}
        }
        """;

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Fresh.java", 4, 0, FileDiff.ChangeType.ADDED, "@@ -0,0 +1,4 @@\n+class Fresh {\n+void first() {}\n+void second() {}\n+}"), src));

    TypeChange type = result.files().get(0).types().get(0);
    assertTrue(type.changed());
    assertTrue(type.methods().stream().allMatch(MemberChange::changed));
  }

  @Test
  void deletedJavaFileIsNotParsed() {
    FileDiff diff = new FileDiff("Gone.java", 0, 10, FileDiff.ChangeType.DELETED, "@@ -1,10 +0,0 @@");
    CodeContext ctx = new CodeContext(SHA, List.of(
        new ChangedFileSource(diff, ChangedFileSource.Availability.DELETED_FILE, null)));

    JavaChangeAnalysis result = analyzer.analyze(ctx);

    assertEquals(JavaFileChange.State.DELETED, result.files().get(0).state());
    assertTrue(result.files().get(0).types().isEmpty());
  }

  @Test
  void nonJavaFileIsSkippedDeterministically() {
    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("config/application.yml", 2, 0, FileDiff.ChangeType.MODIFIED, "@@ -1 +1,2 @@"), "key: value"));

    assertEquals(JavaFileChange.State.NON_JAVA, result.files().get(0).state());
  }

  @Test
  void emptyJavaSourceProducesEmptyState() {
    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Blank.java", 0, 0, FileDiff.ChangeType.MODIFIED, ""), ""));

    assertEquals(JavaFileChange.State.EMPTY, result.files().get(0).state());
  }

  @Test
  void malformedJavaDegradesToParseFailedWithoutCrashing() {
    String bad = "class Broken { void oops( { not java (((  ";
    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Broken.java", 5, 0, FileDiff.ChangeType.MODIFIED, "@@ -1 +1 @@"), bad));

    assertEquals(JavaFileChange.State.PARSE_FAILED, result.files().get(0).state());
    assertTrue(result.files().get(0).types().isEmpty());
  }

  @Test
  void unavailableSourceDegradesToSourceUnavailable() {
    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Missing.java", 1, 0, FileDiff.ChangeType.MODIFIED, "@@ -1 +1 @@"), null));

    assertEquals(JavaFileChange.State.SOURCE_UNAVAILABLE, result.files().get(0).state());
  }

  @Test
  void oneBadFileDoesNotBreakOthers() {
    String good = "class Fine { void ok() {} }";
    String bad = "class Broken { void oops( {";
    CodeContext ctx = new CodeContext(SHA, List.of(
        new ChangedFileSource(new FileDiff("Broken.java", 1, 0, FileDiff.ChangeType.MODIFIED, ""),
            ChangedFileSource.Availability.RETRIEVED, bad.getBytes(StandardCharsets.UTF_8)),
        new ChangedFileSource(new FileDiff("Fine.java", 1, 0, FileDiff.ChangeType.MODIFIED, ""),
            ChangedFileSource.Availability.RETRIEVED, good.getBytes(StandardCharsets.UTF_8))));

    JavaChangeAnalysis result = analyzer.analyze(ctx);

    assertEquals(JavaFileChange.State.PARSE_FAILED, result.files().get(0).state());
    assertEquals(JavaFileChange.State.PARSED, result.files().get(1).state());
  }

  @Test
  void recordComponentsCarryShaUnchanged() {
    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("A.java", 1, 0, FileDiff.ChangeType.ADDED, ""), "class A {}"));

    assertEquals(SHA, result.commitSha(), "context must preserve the analyzed SHA");
  }

  @Test
  void annotationAndRecordKindsAreDetected() {
    String src = """
        @interface Marker {}
        record Point(int x, int y) {}
        """;

    JavaChangeAnalysis result = analyzer.analyze(ctx(
        new FileDiff("Kinds.java", 2, 0, FileDiff.ChangeType.MODIFIED, "@@ -1 +1,2 @@"), src));

    assertTrue(result.files().get(0).types().stream()
        .anyMatch(t -> t.kind() == TypeChange.Kind.ANNOTATION));
    assertTrue(result.files().get(0).types().stream()
        .anyMatch(t -> t.kind() == TypeChange.Kind.RECORD));
  }

  @Test
  void patchHunkParsesSingleAndMultipleCounts() {
    List<PatchHunk> hunks = PatchHunk.parse("@@ -1 +1,3 @@\n-a\n+a\n+b\n@@ -10,2 +12 @@\n-x\n");

    assertEquals(2, hunks.size());
    assertEquals(1, hunks.get(0).oldStart());
    assertEquals(1, hunks.get(0).oldLength());
    assertEquals(1, hunks.get(0).newStart());
    assertEquals(3, hunks.get(0).newLength());
    assertEquals(10, hunks.get(1).oldStart());
    assertEquals(2, hunks.get(1).oldLength());
    assertEquals(12, hunks.get(1).newStart());
    assertEquals(1, hunks.get(1).newLength());
  }

  @Test
  void patchHunkMissingPatchYieldsNoHunks() {
    assertTrue(PatchHunk.parse(null).isEmpty());
    assertTrue(PatchHunk.parse("").isEmpty());
    assertTrue(PatchHunk.parse("not-a-diff").isEmpty());
  }
}
