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
  public CodeContext assemble(TenantId tenantId, RepositoryId repositoryId, String commitSha, java.util.List<FileDiff> diff) {
      return assemble(tenantId, repositoryId, commitSha, diff, 0);
  }

  public CodeContext assemble(TenantId tenantId, RepositoryId repositoryId, String commitSha, java.util.List<FileDiff> diff, int maxDepth) {
    java.util.List<ChangedFileSource> files = new ArrayList<>();
    Set<String> processedPaths = new java.util.HashSet<>();

    for (FileDiff fileDiff : (diff == null ? java.util.List.<FileDiff>of() : diff)) {
      files.add(resolve(tenantId, repositoryId, commitSha, fileDiff));
      processedPaths.add(fileDiff.path());
    }

    if (maxDepth > 0) {
      String sourceRoot = discoverSourceRoot(processedPaths);
      java.util.List<ChangedFileSource> currentLayer = new ArrayList<>(files);

      for (int depth = 0; depth < maxDepth; depth++) {
        Set<String> nextPaths = new java.util.HashSet<>();
        for (ChangedFileSource file : currentLayer) {
          if (file.availability() == ChangedFileSource.Availability.RETRIEVED && file.path().endsWith(".java")) {
            String content = new String(file.contentOptional().orElse(new byte[0]), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("import\\s+([a-zA-Z0-9_.]+);").matcher(content);
            while (m.find()) {
              String imported = m.group(1);
              String p = sourceRoot + imported.replace('.', '/') + ".java";
              if (!processedPaths.contains(p)) {
                nextPaths.add(p);
              }
            }
          }
        }

        if (nextPaths.isEmpty()) break;

        currentLayer = new ArrayList<>();
        for (String p : nextPaths) {
          processedPaths.add(p);
          FileDiff unmodifiedDiff = new FileDiff(p, 0, 0, FileDiff.ChangeType.UNMODIFIED);
          ChangedFileSource fetched = resolve(tenantId, repositoryId, commitSha, unmodifiedDiff);
          files.add(fetched);
          currentLayer.add(fetched);
        }
      }
    }

    // Downstream caller discovery (bounded repository scan)
    int MAX_CANDIDATE_FILES = 500;
    int MAX_PARSED_FILES = 50;
    long MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024; // 5 MB

    int candidateFilesScanned = 0;
    int parsedFilesAdded = 0;
    long downloadedBytes = 0;
    com.cdi.analysis.domain.CoverageState coverage = com.cdi.analysis.domain.CoverageState.FULL;

    Set<String> changedClassNames = new java.util.HashSet<>();
    for (FileDiff fileDiff : (diff == null ? java.util.List.<FileDiff>of() : diff)) {
        if (fileDiff.path().endsWith(".java")) {
            String name = fileDiff.path().substring(fileDiff.path().lastIndexOf('/') + 1);
            if (name.endsWith(".java")) name = name.substring(0, name.length() - 5);
            changedClassNames.add(name);
        }
    }

    if (!changedClassNames.isEmpty()) {
        try {
          java.util.List<String> allPaths = sourceControlPort.listFiles(tenantId, repositoryId, commitSha);
          for (String p : allPaths) {
            if (!p.endsWith(".java") || processedPaths.contains(p)) continue;
            
            if (candidateFilesScanned >= MAX_CANDIDATE_FILES || downloadedBytes >= MAX_DOWNLOAD_BYTES || parsedFilesAdded >= MAX_PARSED_FILES) {
                coverage = com.cdi.analysis.domain.CoverageState.PARTIAL;
                break;
            }
            
            FileDiff unmodifiedDiff = new FileDiff(p, 0, 0, FileDiff.ChangeType.UNMODIFIED);
            ChangedFileSource fetched = resolve(tenantId, repositoryId, commitSha, unmodifiedDiff);
            candidateFilesScanned++;
            
            if (fetched.availability() == ChangedFileSource.Availability.RETRIEVED) {
                byte[] contentBytes = fetched.contentOptional().orElse(new byte[0]);
                downloadedBytes += contentBytes.length;
                String contentStr = new String(contentBytes, java.nio.charset.StandardCharsets.UTF_8);
                
                boolean isCandidate = false;
                for (String className : changedClassNames) {
                    if (contentStr.contains(className)) {
                        isCandidate = true;
                        break;
                    }
                }
                
                if (isCandidate) {
                    files.add(fetched);
                    processedPaths.add(p);
                    parsedFilesAdded++;
                }
            }
          }
        } catch (Exception e) {
            coverage = com.cdi.analysis.domain.CoverageState.PARTIAL;
        }
    }

    return new CodeContext(commitSha, files, coverage);
  }

  private String discoverSourceRoot(Set<String> paths) {
    for (String p : paths) {
      if (p.endsWith(".java") && p.contains("src/main/java/")) {
        return p.substring(0, p.indexOf("src/main/java/") + "src/main/java/".length());
      }
    }
    return "src/main/java/";
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





