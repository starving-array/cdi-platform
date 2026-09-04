package com.cdi.application.analysis;

import com.cdi.analysis.domain.ChangedFileSource;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles the {@link CodeContext} for one analysis run by retrieving each
 * changed file's content at the exact snapshot commit.
 *
 * <p>Follows the existing error/degradation convention of the pipeline
 * (application-layer.md §9): per-file retrieval failures degrade that file's
 * {@link ChangedFileSource.Availability} without failing the run, and content
 * is NEVER substituted from another commit — the requested SHA is passed
 * through unchanged for every retrieval.
 *
 * <p>Retrieval is skipped for deleted files (no content exists at the
 * snapshot) and for known non-textual files (by extension); everything else
 * is retrieved via {@link SourceControlPort#getFileContent}.
 */
public final class CodeContextAssembler {

  /** Extensions treated as binary/non-textual; content retrieval is skipped. */
  private static final Set<String> NON_TEXTUAL_EXTENSIONS = Set.of(
      "png", "jpg", "jpeg", "gif", "bmp", "ico", "svgz", "webp",
      "pdf", "zip", "gz", "tar", "7z", "rar",
      "jar", "war", "ear", "class",
      "exe", "dll", "so", "dylib",
      "woff", "woff2", "ttf", "eot",
      "mp3", "mp4", "mov", "avi", "wav",
      "bin", "dat", "db", "sqlite");

  private final SourceControlPort sourceControlPort;

  public CodeContextAssembler(SourceControlPort sourceControlPort) {
    this.sourceControlPort = Objects.requireNonNull(sourceControlPort, "SourceControlPort");
  }

  /**
   * Builds the code context for {@code commitSha} from the diff. The exact
   * requested SHA is passed to every retrieval — never a branch or HEAD.
   */
  public CodeContext assemble(TenantId tenantId, RepositoryId repositoryId,
                              String commitSha, List<FileDiff> diff) {
    List<ChangedFileSource> files = new ArrayList<>();
    for (FileDiff fileDiff : (diff == null ? List.<FileDiff>of() : diff)) {
      files.add(resolve(tenantId, repositoryId, commitSha, fileDiff));
    }
    return new CodeContext(commitSha, files);
  }

  private ChangedFileSource resolve(TenantId tenantId, RepositoryId repositoryId,
                                    String commitSha, FileDiff fileDiff) {
    if (fileDiff.changeType() == FileDiff.ChangeType.DELETED) {
      return new ChangedFileSource(fileDiff, ChangedFileSource.Availability.DELETED_FILE, null);
    }
    if (isNonTextual(fileDiff.path())) {
      return new ChangedFileSource(fileDiff, ChangedFileSource.Availability.NON_TEXTUAL, null);
    }
    try {
      byte[] content = sourceControlPort.getFileContent(tenantId, repositoryId, fileDiff.path(), commitSha);
      return new ChangedFileSource(fileDiff, ChangedFileSource.Availability.RETRIEVED,
          content == null ? new byte[0] : content);
    } catch (RuntimeException e) {
      // Existing degradation convention: fail-soft per file, run continues.
      String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
      ChangedFileSource.Availability availability = message.contains("not found")
          ? ChangedFileSource.Availability.NOT_FOUND
          : ChangedFileSource.Availability.RETRIEVAL_FAILED;
      return new ChangedFileSource(fileDiff, availability, null);
    }
  }

  private static boolean isNonTextual(String path) {
    int idx = path.lastIndexOf('.');
    if (idx < 0 || idx == path.length() - 1) {
      return false; // no extension: treat as textual (e.g. Dockerfile, Makefile)
    }
    return NON_TEXTUAL_EXTENSIONS.contains(path.substring(idx + 1).toLowerCase(Locale.ROOT));
  }
}
