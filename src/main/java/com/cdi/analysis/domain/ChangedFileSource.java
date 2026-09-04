package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;

import java.util.Optional;

/**
 * One changed file inside a {@link CodeContext}: the {@link FileDiff} plus the
 * outcome of retrieving the file's content at the exact run commit.
 *
 * <p>Unavailability is represented explicitly (never substituted with other
 * commits): deleted files are skipped, known non-textual files are not
 * retrieved, and retrieval failures degrade the entry while the rest of the
 * context is preserved.
 */
public record ChangedFileSource(FileDiff diff, Availability availability, byte[] content) {

  public enum Availability {
    RETRIEVED,
    DELETED_FILE,
    NON_TEXTUAL,
    NOT_FOUND,
    RETRIEVAL_FAILED
  }

  public ChangedFileSource {
    if (diff == null) {
      throw new DomainException("Changed file diff cannot be null");
    }
    if (availability == null) {
      throw new DomainException("Source availability cannot be null");
    }
    content = availability == Availability.RETRIEVED
        ? (content == null ? new byte[0] : content.clone())
        : (content == null ? new byte[0] : content.clone());
  }

  /** Convenience accessor: the file path (owned by the composed diff). */
  public String path() {
    return diff.path();
  }

  /**
   * The file content, present only when {@code availability == RETRIEVED}.
   * (The raw record accessor {@code content()} returns the byte array
   * directly; clone semantics are preserved in both.)
   */
  public Optional<byte[]> contentOptional() {
    return availability == Availability.RETRIEVED
        ? Optional.of(content.clone())
        : Optional.empty();
  }
}
