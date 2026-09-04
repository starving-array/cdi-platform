package com.cdi.analysis.domain.parsing;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.FileDiff;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PART 6: file-local dependency/dependency-import extraction from parsed Java
 * source. Deterministic; no network, no fuzzing.
 */
class JavaDependencyExtractionTest {

  private static final String SHA = "e6c2584f24584da6017c3b89140e96aa1416b7b4";

  private final JavaChangeAnalyzer analyzer = new JavaChangeAnalyzer();

  private JavaFileChange analyzeOne(String path, String source) {
    CodeContext ctx = new CodeContext(SHA, List.of(new ChangedFileSource(
        new FileDiff(path, 1, 0, FileDiff.ChangeType.MODIFIED, ""),
        source == null ? ChangedFileSource.Availability.RETRIEVAL_FAILED
            : ChangedFileSource.Availability.RETRIEVED,
        source == null ? null : source.getBytes(StandardCharsets.UTF_8))));
    return analyzer.analyze(ctx).files().get(0);
  }

  @Test
  void severalImportsArePreservedInOrder() {
    JavaFileChange f = analyzeOne("src/Payment.java", """
        package com.demo;
        import com.demo.payment.Payment;
        import com.demo.validator.PaymentValidator;
        import com.demo.audit.AuditService;
        public class PaymentUse { Payment p; PaymentValidator v; AuditService a; }
        """);

    assertEquals(JavaFileChange.State.PARSED, f.state());
    assertEquals(
        List.of("com.demo.payment.Payment", "com.demo.validator.PaymentValidator",
            "com.demo.audit.AuditService"),
        f.importDeclarations().stream().map(ImportDecl::name).toList());
    assertTrue(f.importDeclarations().stream().noneMatch(ImportDecl::isStatic));
    assertTrue(f.importDeclarations().stream().noneMatch(ImportDecl::isWildcard));
  }

  @Test
  void staticAndWildcardImportsAreFlagged() {
    JavaFileChange f = analyzeOne("x/Test.java", """
        import static java.util.Collections.emptyList;
        import java.util.*;
        class UsesStatics { List<?> l = emptyList(); }
        """);

    assertEquals(2, f.importDeclarations().size());
    ImportDecl st = f.importDeclarations().get(0);
    assertTrue(st.isStatic());
    assertFalse(st.isWildcard());
    ImportDecl wild = f.importDeclarations().get(1);
    assertFalse(wild.isStatic());
    assertTrue(wild.isWildcard());
  }

  @Test
  void noImportFileWorks() {
    JavaFileChange f = analyzeOne("A.java", "class A { int x; }");

    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.imports().isEmpty());
  }

  @Test
  void directReferencedTypesAreExtracted() {
    JavaFileChange f = analyzeOne("src/Pay.java", """
        package p;
        import com.t.Payment;
        import com.v.PaymentValidator;
        class Pay {
            Payment payment;
            PaymentValidator validator;
            java.util.List<String> names;
            void audit(com.audit.AuditService svc) {}
            AuditService fieldBased;
            class AuditService {}
        }
        """);

    assertTrue(f.referencedTypes().contains("Payment"));
    assertTrue(f.referencedTypes().contains("PaymentValidator"));
    assertTrue(f.referencedTypes().contains("java.util.List"));
    assertTrue(f.referencedTypes().contains("com.audit.AuditService"));
    // Type-argument references (e.g. List<String>) are also surfaced — they
    // are direct type references and belong in file-local dependency info.
    assertTrue(f.referencedTypes().contains("String"));
  }

  @Test
  void multipleTypesInOneFileAllReported() {
    JavaFileChange f = analyzeOne("t/Two.java", """
        package t;
        class One { Two two; }
        class Two { One one; }
        """);

    assertEquals(2, f.types().size());
    assertTrue(f.referencedTypes().contains("One"));
    assertTrue(f.referencedTypes().contains("Two"));
  }

  @Test
  void malformedJavaProducesParseFailedWithoutDependencyInvention() {
    JavaFileChange f = analyzeOne("B.java", "class B { void broken( {");

    assertEquals(JavaFileChange.State.PARSE_FAILED, f.state());
    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.referencedTypes().isEmpty());
    assertTrue(f.types().isEmpty());
  }

  @Test
  void nonJavaFileKnobsUnchanged() {
    JavaFileChange f = analyzeOne("conf/app.yml", "k: v");

    assertEquals(JavaFileChange.State.NON_JAVA, f.state());
    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.referencedTypes().isEmpty());
  }

  @Test
  void deletedJavaFileKnobsUnchanged() {
    CodeContext ctx = new CodeContext(SHA, List.of(new ChangedFileSource(
        new FileDiff("Gone.java", 0, 5, FileDiff.ChangeType.DELETED, ""),
        ChangedFileSource.Availability.DELETED_FILE, null)));

    JavaFileChange f = analyzer.analyze(ctx).files().get(0);

    assertEquals(JavaFileChange.State.DELETED, f.state());
    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.referencedTypes().isEmpty());
  }

  @Test
  void unavailableSourceKnobsUnchanged() {
    JavaFileChange f = analyzeOne("Miss.java", null);

    assertEquals(JavaFileChange.State.SOURCE_UNAVAILABLE, f.state());
    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.referencedTypes().isEmpty());
  }

  @Test
  void exactCommitShaIsPropagatedIntoAnalysis() {
    JavaChangeAnalysis a = analyzer.analyze(new CodeContext(SHA, List.of(
        new ChangedFileSource(new FileDiff("A.java", 1, 0, FileDiff.ChangeType.MODIFIED, ""),
            ChangedFileSource.Availability.RETRIEVED,
            "class A {}".getBytes(StandardCharsets.UTF_8)))));

    assertEquals(SHA, a.commitSha());
  }

  @Test
  void backwardCompatibleSixArgConstructorStillWorks() {
    JavaFileChange f = new JavaFileChange("A.java", JavaFileChange.State.PARSED, "p",
        List.of("java.util.List"),
        List.of(), List.of());

    assertTrue(f.importDeclarations().isEmpty());
    assertTrue(f.referencedTypes().isEmpty());
    assertEquals(List.of("java.util.List"), f.imports());
  }
}
