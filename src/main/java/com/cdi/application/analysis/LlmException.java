package com.cdi.application.analysis;

/** Base exception for all LLM‑related errors. */
public class LlmException extends Exception {
  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}