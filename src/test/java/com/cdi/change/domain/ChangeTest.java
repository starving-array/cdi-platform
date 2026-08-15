package com.cdi.change.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTest {

    @Test
    void shouldCreateValidChange() {
        Change change = new Change(
            ChangeId.generate(), RepositoryId.generate(), "PR-123",
            "Fix bug", "Desc", "alice", "feature-x", "main",
            "abc123sha", Instant.now()
        );
        
        assertEquals(Change.Status.OPEN, change.getStatus());
        assertEquals("abc123sha", change.getLatestCommitSha());
    }

    @Test
    void shouldUpdateLatestCommit() {
        Change change = new Change(
            ChangeId.generate(), RepositoryId.generate(), "PR-123",
            "Fix bug", "Desc", "alice", "feature-x", "main",
            "abc123sha", Instant.now()
        );
        
        change.updateLatestCommit("def456sha", Instant.now());
        assertEquals("def456sha", change.getLatestCommitSha());
    }

    @Test
    void shouldNotUpdateCommitOnMergedChange() {
        Change change = new Change(
            ChangeId.generate(), RepositoryId.generate(), "PR-123",
            "Fix bug", "Desc", "alice", "feature-x", "main",
            "abc123sha", Instant.now()
        );
        
        change.merge(Instant.now());
        assertThrows(DomainException.class, () -> change.updateLatestCommit("def456sha", Instant.now()));
    }

    @Test
    void shouldTransitionStateProperly() {
        Change change = new Change(
            ChangeId.generate(), RepositoryId.generate(), "PR-123",
            "Fix bug", "Desc", "alice", "feature-x", "main",
            "abc123sha", Instant.now()
        );
        
        change.close(Instant.now());
        assertEquals(Change.Status.CLOSED, change.getStatus());
        
        change.reopen(Instant.now());
        assertEquals(Change.Status.OPEN, change.getStatus());
        
        change.merge(Instant.now());
        assertEquals(Change.Status.MERGED, change.getStatus());
        
        assertThrows(DomainException.class, () -> change.close(Instant.now()));
    }
}
