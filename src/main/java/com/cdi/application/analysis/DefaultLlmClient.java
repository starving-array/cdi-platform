package com.cdi.application.analysis;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Real LLM client that performs an HTTP POST to a model endpoint.
 * <p>
 * Behaviour:
 * <ul>
 *   <li>If the configured API key is empty, throws {@link LlmException}</li>
 *   <li>Otherwise, sends the prompt to the configured endpoint using the model specified in the config.</li>
 *   <li>Reads the HTTP response body as text.</li>
 *   <li>If the status code is not 200, throws {@link LlmException}</li>
 *   <li>If the response body is empty or whitespace‑only, throws {@link LlmException}</li>
 *   <li>Otherwise returns the raw text for the adapter to parse.</li>
 * </ul>
 */
public final class DefaultLlmClient implements LlmClient {

  private final LlmConfig config;

  public DefaultLlmClient(LlmConfig config) {
    this.config = config;
  }

  @Override
  public String invoke(String prompt) throws LlmException {
    if (config.apiKey() == null || config.apiKey().isBlank()) {
      throw new LlmException(
          "LLM API key not configured; set llm.api.key in application.yml or as an environment variable");
    }

    try {
      URL url = new URL(config.endpoint());
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json; utf-8");
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("Authorization", "Bearer " + config.apiKey());
      conn.setConnectTimeout((int) config.timeout().toMillis());
      conn.setReadTimeout((int) config.timeout().toMillis());

      StringBuilder jsonInput = new StringBuilder();
      jsonInput.append("{\"model\":\"");
      jsonInput.append(config.model());
      jsonInput.append("\",\"messages\":[{\"role\":\"user\",\"content\":\"");
      jsonInput.append(escapeJson(prompt));
      jsonInput.append("\"}],\"max_tokens\":800,\"temperature\":0.2}");

      try (OutputStream os = conn.getOutputStream()) {
        byte[] input = jsonInput.toString().getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
          String errLine;
          StringBuilder errBuilder = new StringBuilder();
          while ((errLine = br.readLine()) != null) {
            errBuilder.append(errLine).append("\n");
          }
          throw new LlmException(
              "LLM API returned HTTP " + responseCode + ": " + errBuilder.toString());
        }
      }

      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
          response.append(line).append("\n");
        }
        String text = response.toString().trim();
        if (text.isEmpty()) {
          throw new LlmException("LLM API returned empty response");
        }
        return text;
      }
    } catch (LlmException e) {
      throw e;
    } catch (IOException e) {
      throw new LlmException("LLM API I/O error: " + e.getMessage(), e);
    }
  }

  /** Very simple JSON‑string escaping for the prompt payload. */
  private String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}