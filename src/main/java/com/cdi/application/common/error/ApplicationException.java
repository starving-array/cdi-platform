package com.cdi.application.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * Exception carrying exactly one {@link ApplicationError} plus optional
 * structured details (application-layer.md §9.1).
 *
 * <p>This is the only failure raised by the orchestration layer towards the
 * API layer. It never exposes HTTP, Spring, JPA, provider SDK, or
 * persistence exceptions.
 */
public class ApplicationException extends RuntimeException {

  private final ApplicationError error;
  private final Map<String, ?> details;

  public ApplicationException(ApplicationError error) {
    this(error, null, Map.of());
  }

  public ApplicationException(ApplicationError error, String message) {
    this(error, message, Map.of());
  }

  public ApplicationException(ApplicationError error, Map<String, ?> details) {
    this(error, null, details);
  }

  public ApplicationException(ApplicationError error, String message, Map<String, ?> details) {
    super(message != null && !message.isBlank() ? message : error.defaultMessage());
    this.error = Objects.requireNonNull(error, "ApplicationError cannot be null");
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  /** The single canonical error represented by this failure. */
  public ApplicationError getError() {
    return error;
  }

  /** Structured, immutable context details (may be empty). */
  public Map<String, ?> getDetails() {
    return details;
  }
}