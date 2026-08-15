package com.cdi.application.common.error;

import java.util.Objects;

/**
 * Failure transported out of an outbound port adapter.
 *
 * <p>Ports never expose provider-specific exceptions (GitHub SDK, LLM SDK,
 * JPA, SQL, vector-store). The orchestration layer catches this type at each
 * call site and applies the port's documented retry/degradation policy later
 * — no retry loops are executed by this class or by the ports themselves.
 */
public class PortException extends RuntimeException {

  private final PortType port;
  private final boolean retryable;

  public PortException(PortType port, boolean retryable, String message) {
    super(message);
    this.port = Objects.requireNonNull(port, "PortType cannot be null");
    this.retryable = retryable;
  }

  public PortException(PortType port, boolean retryable, String message, Throwable cause) {
    super(message, cause);
    this.port = Objects.requireNonNull(port, "PortType cannot be null");
    this.retryable = retryable;
  }

  /** The outbound port that failed. */
  public PortType getPort() {
    return port;
  }

  /** Whether the documented application policy permits a retry. */
  public boolean isRetryable() {
    return retryable;
  }
}