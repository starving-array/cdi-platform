package com.cdi.application.analysis;

import java.time.Duration;

/**
 * Configuration for the LLM investigation client.
 * Values are resolved from Spring Environment (application.yml or
 * system properties / environment variables as fall-back).
 */
public record LlmConfig(
        String endpoint,
        String model,
        Duration timeout,
        String apiKey) {

  public LlmConfig {
    endpoint = endpoint != null ? endpoint.trim() : "";
    model = model != null ? model.trim() : "default";
    timeout = timeout != null ? timeout : Duration.ofMinutes(5);
    apiKey = apiKey != null ? apiKey.trim() : "";
  }

  /** Returns true if an API key is configured (non-blank). */
  public boolean hasApiKey() {
    return !apiKey.isBlank();
  }

  /** Default config used when no LLM configuration is present. */
  public static LlmConfig defaultConfig() {
    return new LlmConfig("", "default", Duration.ofMinutes(5), "");
  }
}