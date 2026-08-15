package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDiffTest {

  @Test
  void shouldCreateValidFileDiff() {
    FileDiff diff = new FileDiff("src/main/App.java", 10, 2, FileDiff.ChangeType.MODIFIED);

    assertEquals("src/main/App.java", diff.path());
    assertEquals(10, diff.additions());
    assertEquals(2, diff.deletions());
    assertEquals(FileDiff.ChangeType.MODIFIED, diff.changeType());
  }

  @Test
  void shouldTrimPath() {
    FileDiff diff = new FileDiff("  src/App.java  ", 0, 0, FileDiff.ChangeType.ADDED);

    assertEquals("src/App.java", diff.path());
  }

  @Test
  void shouldRejectBlankPath() {
    assertThrows(DomainException.class,
        () -> new FileDiff("  ", 0, 0, FileDiff.ChangeType.ADDED));
  }

  @Test
  void shouldRejectNegativeCounts() {
    assertThrows(DomainException.class,
        () -> new FileDiff("a", -1, 0, FileDiff.ChangeType.ADDED));
    assertThrows(DomainException.class,
        () -> new FileDiff("a", 0, -1, FileDiff.ChangeType.DELETED));
  }

  @Test
  void shouldRejectNullChangeType() {
    assertThrows(DomainException.class,
        () -> new FileDiff("a", 0, 0, null));
  }
}