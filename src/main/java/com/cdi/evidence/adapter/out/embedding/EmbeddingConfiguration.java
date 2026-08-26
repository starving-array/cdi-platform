package com.cdi.evidence.adapter.out.embedding;

import com.cdi.application.port.out.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring configuration activating the appropriate {@link EmbeddingPort} bean.
 */
@Configuration
public class EmbeddingConfiguration {

  @Bean
  @ConditionalOnProperty(name = "cdi.embedding.provider", havingValue = "onnx", matchIfMissing = true)
  public EmbeddingPort onnxEmbeddingAdapter(
      @Value("${cdi.embedding.model-path:classpath:models/all-MiniLM-L6-v2/model.onnx}") String modelPath,
      @Value("${cdi.embedding.model-version:all-MiniLM-L6-v2-onnx-v1}") String modelVersion,
      ResourceLoader resourceLoader) {
    return new OnnxEmbeddingAdapter(modelPath, modelVersion, resourceLoader);
  }

  @Bean
  @ConditionalOnProperty(name = "cdi.embedding.provider", havingValue = "deterministic")
  public EmbeddingPort deterministicEmbeddingAdapter() {
    return new DeterministicEmbeddingAdapter();
  }
}