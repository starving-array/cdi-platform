package com.cdi.application.common.error;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationExceptionTest {

  @Test
  void shouldCarryTheSingleErrorAndDefaultMessage() {
    ApplicationException ex = new ApplicationException(ApplicationError.CHANGE_NOT_FOUND);

    assertEquals(ApplicationError.CHANGE_NOT_FOUND, ex.getError());
    assertEquals(ApplicationError.CHANGE_NOT_FOUND.defaultMessage(), ex.getMessage());
    assertTrue(ex.getDetails().isEmpty());
  }

  @Test
  void shouldSupportCustomMessageAndStructuredDetails() {
    ApplicationException ex = new ApplicationException(
        ApplicationError.UNAUTHORIZED,
        "Engineer cannot override decisions",
        Map.of("requiredRole", "TENANT_ADMIN"));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertEquals("Engineer cannot override decisions", ex.getMessage());
    assertEquals("TENANT_ADMIN", ex.getDetails().get("requiredRole"));
  }

  @Test
  void shouldRejectMissingError() {
    assertThrows(NullPointerException.class, () -> new ApplicationException(null));
  }

  @Test
  void shouldKeepDetailsImmutable() {
    Map<String, String> mutable = new java.util.HashMap<>();
    mutable.put("analysisRunId", "abc");
    ApplicationException ex = new ApplicationException(ApplicationError.ANALYSIS_SUPERSEDED, mutable);

    @SuppressWarnings("unchecked")
    Map<String, Object> details = (Map<String, Object>) ex.getDetails();
    assertThrows(UnsupportedOperationException.class, () -> details.put("x", "y"));
  }
}