package com.cdi.application.port.out;

import com.cdi.application.common.IdempotencyKey;

/**
 * Outbound port for enqueuing asynchronous application commands
 * (ports-and-adapters.md §2.5, application-layer.md §7). Same-database queue;
 * hides the worker framework details (Spring Batch, JobRunr, custom Postgres
 * queue). No heavyweight broker (no Kafka).
 *
 * <p><b>Failure policy:</b> database unavailable bubbles up to the caller
 * (e.g., webhook 500; SCM retries delivery). {@link #cancel} marks a
 * pending/running job as obsolete.
 */
public interface JobQueuePort {

  JobId enqueue(String commandName, Object payload, IdempotencyKey key);

  void cancel(IdempotencyKey key);
}