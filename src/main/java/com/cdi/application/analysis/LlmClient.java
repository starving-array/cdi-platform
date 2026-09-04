package com.cdi.application.analysis;

/**
 * Abstraction for LLM invocation. The concrete implementation
 * (DefaultLlmClient) performs an actual HTTP call; a
 * FakeLlmClient is provided for deterministic testing.
 * <p>
 * The contract is purposefully minimal: a single method that
 * takes a prompt string and returns the model's response text.
 * Any provider‑specific details (authentication, HTTP details,
 * parsing) are encapsulated in the implementation.
 */
@FunctionalInterface
public interface LlmClient {

  /**
   * Invoke the LLM with the given prompt.
   *
   * @param prompt the investigation prompt containing code intelligence
   * @return the model's raw response text
   * @throws LlmException if the call fails (network, HTTP error,
   *   timeout, malformed response, missing configuration, etc.)
   */
  String invoke(String prompt) throws LlmException;
}