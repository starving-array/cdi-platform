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
    assertFalse(ApplicationError.ANALYSIS_RUN_NOT_FOUND.retryable());
    assertFalse(ApplicationError.ORGANIZATION_NOT_FOUND.retryable());
    assertFalse(ApplicationError.ORGANIZATION_ALREADY_SUSPENDED.retryable());
    assertFalse(ApplicationError.REPOSITORY_NOT_FOUND.retryable());
    assertFalse(ApplicationError.REPOSITORY_ALREADY_ARCHIVED.retryable());
    assertFalse(ApplicationError.SERVICE_NOT_FOUND.retryable());
    assertFalse(ApplicationError.SERVICE_ALREADY_DEPRECATED.retryable());
    assertFalse(ApplicationError.POLICY_NOT_FOUND.retryable());
    assertFalse(ApplicationError.ANALYSIS_ALREADY_RUNNING.retryable());
    assertFalse(ApplicationError.ANALYSIS_SUPERSEDED.retryable());
    assertFalse(ApplicationError.INSUFFICIENT_CONTEXT.retryable());
    assertFalse(ApplicationError.EVIDENCE_COLLECTION_FAILED.retryable());
    assertFalse(ApplicationError.POLICY_EVALUATION_FAILED.retryable());
    assertFalse(ApplicationError.DECISION_NOT_FOUND.retryable());
    assertFalse(ApplicationError.DECISION_ALREADY_OVERRIDDEN.retryable());
    assertFalse(ApplicationError.UNAUTHORIZED.retryable());
  }

  @Test
  void shouldCoverTheDocumentedCanonicalCatalog() {
    assertEquals(18, ApplicationError.values().length);
  }
}