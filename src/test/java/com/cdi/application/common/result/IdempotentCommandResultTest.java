package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotentCommandResultTest {

  @Test
  void shouldRepresentCreatedRun() {
    AnalysisRunId runId = AnalysisRunId.generate();
    IdempotentCommandResult result = new IdempotentCommandResult(runId, true);

    assertTrue(result instanceof CommandResult);
    assertEquals(runId, result.analysisRunId());
    assertTrue(result.created());
  }

  @Test
  void shouldRepresentIdempotentReuse() {
    IdempotentCommandResult result = new IdempotentCommandResult(AnalysisRunId.generate(), false);

    assertFalse(result.created());
  }

  @Test
  void shouldRejectMissingRunId() {
    assertThrows(DomainException.class, () -> new IdempotentCommandResult(null, true));
  }
}