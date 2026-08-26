package com.cdi.evidence.adapter.out.embedding;

import com.cdi.application.port.out.EmbeddingPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pluggable deterministic in-process embedding implementation (D1).
 * Generates normalized 384-dimensional bag-of-words / token hash vectors.
 * Active only when configured via {@link EmbeddingConfiguration}.
 */
public class DeterministicEmbeddingAdapter implements EmbeddingPort {

  private static final String MODEL_VERSION = "deterministic-bow-384-v1";
  private static final int DIMENSIONS = 384;

  @Override
  public List<Float> embedText(String text) {
    if (text == null || text.isBlank()) {
      return Collections.nCopies(DIMENSIONS, 0.0f);
    }

    float[] vector = new float[DIMENSIONS];
    String normalized = text.toLowerCase();
    String[] tokens = normalized.split("[^a-z0-9_]+");

    Set<String> uniqueTokens = new HashSet<>(Arrays.asList(tokens));
    for (String token : uniqueTokens) {
      if (token.isBlank()) continue;
      int hash = token.hashCode() & 0x7FFFFFFF;
      int bucket1 = hash % DIMENSIONS;
      int bucket2 = (int) ((((long) hash * 31L) + 17L) & 0x7FFFFFFF) % DIMENSIONS;
      vector[bucket1] += 1.0f;
      vector[bucket2] += 0.5f;
    }

    // Normalize to unit vector for cosine distance
    double sumSq = 0.0;
    for (float v : vector) {
      sumSq += v * v;
    }
    double norm = Math.sqrt(sumSq);
    if (norm > 0.0) {
      for (int i = 0; i < DIMENSIONS; i++) {
        vector[i] = (float) (vector[i] / norm);
      }
    }

    List<Float> list = new ArrayList<>(DIMENSIONS);
    for (float v : vector) {
      list.add(v);
    }
    return list;
  }

  @Override
  public String getModelVersion() {
    return MODEL_VERSION;
  }

  @Override
  public int getDimensions() {
    return DIMENSIONS;
  }
}