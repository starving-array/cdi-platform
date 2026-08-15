package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodeSnapshotTest {

    @Test
    void shouldCreateValidCodeSnapshot() {
        CodeSnapshot snapshot = new CodeSnapshot(" abcdef123 ", " main ");
        assertEquals("abcdef123", snapshot.commitSha());
        assertEquals("main", snapshot.branch());
    }

    @Test
    void shouldHandleNullBranch() {
        CodeSnapshot snapshot = new CodeSnapshot("abcdef123", null);
        assertEquals("abcdef123", snapshot.commitSha());
        assertEquals("", snapshot.branch());
    }

    @Test
    void shouldRejectInvalidCommitSha() {
        assertThrows(DomainException.class, () -> new CodeSnapshot(null, "main"));
        assertThrows(DomainException.class, () -> new CodeSnapshot("   ", "main"));
    }
}
