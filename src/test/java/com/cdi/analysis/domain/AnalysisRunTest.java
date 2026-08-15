package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisRunTest {

    @Test
    void shouldCreateValidAnalysisRun() {
        CodeSnapshot snapshot = new CodeSnapshot("abc123sha", "main");
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), snapshot, Instant.now());
        
        assertEquals(AnalysisRun.Status.QUEUED, run.getStatus());
        assertEquals("abc123sha", run.getCodeSnapshot().commitSha());
    }

    @Test
    void shouldExecuteHappyPathTransitions() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        
        run.start();
        assertEquals(AnalysisRun.Status.RUNNING, run.getStatus());
        
        run.complete(Instant.now());
        assertEquals(AnalysisRun.Status.COMPLETED, run.getStatus());
        assertTrue(run.getCompletedAt().isPresent());
    }

    @Test
    void shouldFailFromQueuedOrRunning() {
        AnalysisRun run1 = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run1.fail(new AnalysisFailure(AnalysisFailure.FailureCategory.SOURCE_UNAVAILABLE, "ERR-1", Instant.now()));
        assertEquals(AnalysisRun.Status.FAILED, run1.getStatus());
        assertTrue(run1.getFailureInfo().isPresent());

        AnalysisRun run2 = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("def", "main"), Instant.now());
        run2.start();
        run2.fail(new AnalysisFailure(AnalysisFailure.FailureCategory.ANALYSIS_FAILED, "ERR-2", Instant.now()));
        assertEquals(AnalysisRun.Status.FAILED, run2.getStatus());
    }

    @Test
    void shouldSupersedePendingRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.start();
        run.supersede();
        assertEquals(AnalysisRun.Status.SUPERSEDED, run.getStatus());
    }

    @Test
    void shouldRejectSupersedingCompletedRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.start();
        run.complete(Instant.now());
        
        // A completed run remains historically immutable. It cannot be silently superseded.
        assertThrows(DomainException.class, run::supersede);
    }

    @Test
    void shouldRetryFailedRunBackToQueued() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.fail(new AnalysisFailure(AnalysisFailure.FailureCategory.ANALYSIS_FAILED, "ERR-1", Instant.now()));

        run.retry();
        assertEquals(AnalysisRun.Status.QUEUED, run.getStatus());
    }

    @Test
    void shouldRejectRetryingQueuedRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());

        assertThrows(DomainException.class, run::retry);
        assertEquals(AnalysisRun.Status.QUEUED, run.getStatus());
    }

    @Test
    void shouldRejectRetryingRunningRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.start();

        assertThrows(DomainException.class, run::retry);
        assertEquals(AnalysisRun.Status.RUNNING, run.getStatus());
    }

    @Test
    void shouldRejectRetryingCompletedRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.start();
        run.complete(Instant.now());

        assertThrows(DomainException.class, run::retry);
        assertEquals(AnalysisRun.Status.COMPLETED, run.getStatus());
    }

    @Test
    void shouldRejectRetryingSupersededRun() {
        AnalysisRun run = new AnalysisRun(AnalysisRunId.generate(), ChangeId.generate(), new CodeSnapshot("abc", "main"), Instant.now());
        run.supersede();

        assertThrows(DomainException.class, run::retry);
        assertEquals(AnalysisRun.Status.SUPERSEDED, run.getStatus());
    }
}
