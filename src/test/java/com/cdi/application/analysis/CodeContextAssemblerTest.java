package com.cdi.application.analysis;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PART 4 tests: CodeContext assembly — exact-commit source retrieval,
 * per-file association, and degradation for deleted/binary/missing/failed
 * retrievals.
 */
class CodeContextAssemblerTest {

  private static final String RUN_SHA = "e6c2584f24584da6017c3b89140e96aa1416b7b4";
  private static final String OTHER_SHA = "0000000000000000000000000000000000000000";

  private final RecordingSourceControlPort port = new RecordingSourceControlPort();
  private final CodeContextAssembler assembler = new CodeContextAssembler(port);
  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repoId = RepositoryId.generate();

  @Test
  void codeContextCarriesExactRunCommitSha() {
    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of());

    assertEquals(RUN_SHA, ctx.commitSha());
  }

  @Test
  void codeContextRejectsBlankSha() {
    assertThrows(DomainException.class, () -> new CodeContext("  ", List.of()));
    assertThrows(DomainException.class, () -> new CodeContext(null, List.of()));
  }

  @Test
  void changedSourceFileHasPathPatchAndSourceContent() {
    FileDiff diff = new FileDiff("src/Payment.java", 5, 2, FileDiff.ChangeType.MODIFIED,
        "@@ -1 +1 @@\n-a\n+b");
    port.contents.put("src/Payment.java", "class Payment {}".getBytes(StandardCharsets.UTF_8));

    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(diff));

    assertEquals(1, ctx.changedFiles().size());
    ChangedFileSource file = ctx.changedFiles().get(0);
    assertEquals("src/Payment.java", file.path());
    assertEquals("@@ -1 +1 @@\n-a\n+b", file.diff().patch());
    assertEquals(ChangedFileSource.Availability.RETRIEVED, file.availability());
    assertEquals("class Payment {}",
        new String(file.contentOptional().orElseThrow(), StandardCharsets.UTF_8));
  }

  @Test
  void multipleFilesRetainCorrectPathContentAssociation() {
    port.contents.put("a/A.java", "aaa".getBytes(StandardCharsets.UTF_8));
    port.contents.put("b/B.java", "bbb".getBytes(StandardCharsets.UTF_8));

    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("a/A.java", 1, 0, FileDiff.ChangeType.MODIFIED),
        new FileDiff("b/B.java", 2, 1, FileDiff.ChangeType.MODIFIED)));

    assertEquals(2, ctx.changedFiles().size());
    ChangedFileSource a = ctx.changedFiles().get(0);
    ChangedFileSource b = ctx.changedFiles().get(1);
    assertEquals("a/A.java", a.path());
    assertEquals("aaa", new String(a.contentOptional().orElseThrow(), StandardCharsets.UTF_8));
    assertEquals("b/B.java", b.path());
    assertEquals("bbb", new String(b.contentOptional().orElseThrow(), StandardCharsets.UTF_8));
  }

  @Test
  void addedFileIsRetrieved() {
    port.contents.put("New.java", "new code".getBytes(StandardCharsets.UTF_8));

    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("New.java", 5, 0, FileDiff.ChangeType.ADDED)));

    ChangedFileSource file = ctx.changedFiles().get(0);
    assertEquals(ChangedFileSource.Availability.RETRIEVED, file.availability());
    assertEquals(FileDiff.ChangeType.ADDED, file.diff().changeType());
    assertEquals("new code", new String(file.contentOptional().orElseThrow(), StandardCharsets.UTF_8));
  }

  @Test
  void deletedFileIsNotRetrieved() {
    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("Old.java", 0, 10, FileDiff.ChangeType.DELETED)));

    ChangedFileSource file = ctx.changedFiles().get(0);
    assertEquals(ChangedFileSource.Availability.DELETED_FILE, file.availability());
    assertTrue(file.contentOptional().isEmpty());
    assertTrue(port.requestedPaths.isEmpty(), "no source request for deleted files");
  }

  @Test
  void binaryFileIsSkippedWithoutCrash() {
    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("img/logo.png", 0, 0, FileDiff.ChangeType.MODIFIED)));

    ChangedFileSource file = ctx.changedFiles().get(0);
    assertEquals(ChangedFileSource.Availability.NON_TEXTUAL, file.availability());
    assertTrue(file.contentOptional().isEmpty());
    assertTrue(port.requestedPaths.isEmpty(), "no source request for binary files");
  }

  @Test
  void missingSourceDegradesToNotFound_withoutCrashingRun() {
    port.missingPaths.add("Gone.java");

    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("Gone.java", 3, 1, FileDiff.ChangeType.MODIFIED),
        new FileDiff("Ok.java", 3, 1, FileDiff.ChangeType.MODIFIED)));

    assertEquals(ChangedFileSource.Availability.NOT_FOUND, ctx.changedFiles().get(0).availability());
    assertEquals(ChangedFileSource.Availability.RETRIEVED, ctx.changedFiles().get(1).availability());
  }

  @Test
  void genericRetrievalFailureDegradesToRetrievalFailed() {
    port.failingPaths.add("Boom.java");

    CodeContext ctx = assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("Boom.java", 1, 1, FileDiff.ChangeType.MODIFIED)));

    assertEquals(ChangedFileSource.Availability.RETRIEVAL_FAILED, ctx.changedFiles().get(0).availability());
  }

  @Test
  void sourceRetrievalUsesRunCommitSha_notBranchHead() {
    port.contents.put("a/A.java", "x".getBytes(StandardCharsets.UTF_8));

    assembler.assemble(tenantId, repoId, RUN_SHA, List.of(
        new FileDiff("a/A.java", 1, 0, FileDiff.ChangeType.MODIFIED)));

    assertEquals(1, port.requestedShas.size());
    assertEquals(RUN_SHA, port.requestedShas.get(0));
    assertNotEquals(OTHER_SHA, port.requestedShas.get(0));
  }

  // --- Fake SourceControlPort recording the requested state ---

  private static class RecordingSourceControlPort implements SourceControlPort {

    @Override
    public java.util.List<String> listFiles(com.cdi.common.domain.id.TenantId tenantId, com.cdi.common.domain.id.RepositoryId repositoryId, String commitSha) {
        return java.util.List.of();
    }

    final List<String> requestedPaths = new ArrayList<>();
    final List<String> requestedShas = new ArrayList<>();
    final java.util.Map<String, byte[]> contents = new java.util.HashMap<>();
    final List<String> missingPaths = new ArrayList<>();
    final List<String> failingPaths = new ArrayList<>();

    RecordingSourceControlPort() {
      contents.put("Ok.java", "ok".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ChangeMetadata getChangeMetadata(TenantId tenantId, RepositoryId repositoryId,
                                            String providerChangeId) {
      return new ChangeMetadata(providerChangeId, "t", "d", "a", "feature-x", "main", "sha");
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      return List.of();
    }

    @Override
    public byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path,
                                 String commitSha) {
      requestedPaths.add(path);
      requestedShas.add(commitSha);
      if (missingPaths.contains(path)) {
        throw new DomainException("File not found at requested commit: " + path);
      }
      if (failingPaths.contains(path)) {
        throw new DomainException("Error fetching file content: 500");
      }
      return contents.getOrDefault(path, new byte[0]);
    }

    @Override
    public void publishStatusCheck(TenantId tenantId, RepositoryId repositoryId, String commitSha,
                                   DecisionOutcome outcome, List<DecisionReason> reasons,
                                   String detailsUrl) {
    }
  }
}

