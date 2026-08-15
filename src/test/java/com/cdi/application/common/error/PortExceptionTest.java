package com.cdi.application.common.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortExceptionTest {

  @Test
  void shouldExposePortIdentity() {
    PortException ex = new PortException(PortType.SOURCE_CONTROL, true, "SCM API rate limited");

    assertEquals(PortType.SOURCE_CONTROL, ex.getPort());
    assertTrue(ex.isRetryable());
    assertEquals("SCM API rate limited", ex.getMessage());
  }
}