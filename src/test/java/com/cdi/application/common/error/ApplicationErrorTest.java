package com.cdi.application.common.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationErrorTest {

  @Test
  void shouldExposeStableCodeAndMessageForEveryError() {
    for (ApplicationError error : ApplicationError.values()) {
      assertEquals(error.name(), error.code(), "code must be the stable enum name");
      assertNotNull(error.defaultMessage());
      assertFalse(error.defaultMessage().isBlank(), "every error needs a human-readable message");
    }
  }

  @Test
  void shouldClassifyRetryableErrorsPerApplicationPolicy() {
    assertTrue(ApplicationError.AGENT_INVESTIGATION_FAILED.retryable());

    assertFalse(ApplicationError.CHANGE_NOT_FOUND.retryable());
    assertFalse(ApplicationError.ANALYSIS_ALREADY_RUNNING.retryable());
    assertFalse(ApplicationError.ANALYSIS_SUPERSEDED.retryable());
    assertFalse(ApplicationError.INSUFFICIENT_CONTEXT.retryable());
    assertFalse(ApplicationError.EVIDENCE_COLLECTION_FAILED.retryable());
    assertFalse(ApplicationError.POLICY_EVALUATION_FAILED.retryable());
    assertFalse(ApplicationError.UNAUTHORIZED.retryable());
  }

  @Test
  void shouldCoverTheDocumentedCanonicalCatalog() {
    assertEquals(9, ApplicationError.values().length);
  }
}