package com.cdi.application.port.out;

import java.util.List;

/**
 * Outbound application port for generating text embeddings (D1).
 * Completely decouples the core domain and application layer from specific
 * embedding model providers (e.g. OpenAI, ONNX, Ollama, HuggingFace).
 */
public interface EmbeddingPort {

  /**
   * Generates a dense float vector embedding for the supplied text.
   *
   * @param text input text to embed
   * @return list of floats representing the embedding vector
   */
  List<Float> embedText(String text);

  /**
   * Returns the model identifier/version used for generating embeddings.
   */
  String getModelVersion();

  /**
   * Returns the vector dimensionality of this embedding model (384 per D3).
   */
  int getDimensions();
}