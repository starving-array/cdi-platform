package com.cdi.evidence.adapter.out.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.cdi.application.port.out.EmbeddingPort;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-grade in-process ONNX embedding adapter for {@code all-MiniLM-L6-v2}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Tokenizes text into {@code input_ids}, {@code attention_mask}, and {@code token_type_ids} (max sequence length 256).</li>
 *   <li>Executes ONNX graph inference via Microsoft ONNX Runtime on CPU.</li>
 *   <li>Performs attention-mask-aware mean pooling across the token sequence dimension of {@code last_hidden_state}.</li>
 *   <li>L2-normalizes the resulting 384-dimensional vector for cosine distance computation.</li>
 * </ol>
 *
 * <p>Thread-safe, air-gapped, zero external network calls, zero API credentials.
 */
public class OnnxEmbeddingAdapter implements EmbeddingPort {

  private static final int DIMENSIONS = 384;
  private static final int MAX_SEQ_LEN = 256;

  private final String modelVersion;
  private final OrtEnvironment environment;
  private final OrtSession session;
  private final HuggingFaceTokenizer tokenizer;
  private final Path tempModelFile;

  public OnnxEmbeddingAdapter(
      String modelPath,
      String modelVersion,
      ResourceLoader resourceLoader) {
    this.modelVersion = modelVersion;
    Path createdTempFile = null;
    try {
      this.environment = OrtEnvironment.getEnvironment();

      Resource modelResource = resourceLoader.getResource(modelPath);
      if (!modelResource.exists()) {
        throw new IllegalArgumentException("ONNX model resource not found at: " + modelPath);
      }

      File file;
      try {
        file = modelResource.getFile();
      } catch (Exception e) {
        createdTempFile = Files.createTempFile("all-MiniLM-L6-v2-", ".onnx");
        createdTempFile.toFile().deleteOnExit();
        try (InputStream in = modelResource.getInputStream();
             FileOutputStream out = new FileOutputStream(createdTempFile.toFile())) {
          in.transferTo(out);
        }
        file = createdTempFile.toFile();
      }
      this.tempModelFile = createdTempFile;

      OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
      opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
      this.session = environment.createSession(file.getAbsolutePath(), opts);

      // Load tokenizer from tokenizer.json located alongside the model
      Resource tokenizerResource = resourceLoader.getResource("classpath:models/all-MiniLM-L6-v2/tokenizer.json");
      if (!tokenizerResource.exists()) {
        throw new IllegalStateException("Tokenizer definition not found at classpath:models/all-MiniLM-L6-v2/tokenizer.json");
      }
      try (InputStream tokIn = tokenizerResource.getInputStream()) {
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokIn, Map.of("padding", "true", "truncation", "true", "maxLength", String.valueOf(MAX_SEQ_LEN)));
      }

    } catch (Exception e) {
      if (createdTempFile != null) {
        try { Files.deleteIfExists(createdTempFile); } catch (Exception ignored) {}
      }
      throw new IllegalStateException("Failed to initialize ONNX embedding model from " + modelPath, e);
    }
  }

  @Override
  public List<Float> embedText(String text) {
    if (text == null || text.isBlank()) {
      return Collections.nCopies(DIMENSIONS, 0.0f);
    }

    try {
      Encoding encoding = tokenizer.encode(text);
      long[] inputIds = encoding.getIds();
      long[] attentionMask = encoding.getAttentionMask();
      long[] tokenTypeIds = encoding.getTypeIds();

      int seqLen = inputIds.length;
      long[][] inputIdsBatch = new long[][]{inputIds};
      long[][] attentionMaskBatch = new long[][]{attentionMask};
      long[][] tokenTypeIdsBatch = new long[][]{tokenTypeIds};

      Map<String, OnnxTensor> inputMap = new HashMap<>();
      try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIdsBatch);
           OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, attentionMaskBatch);
           OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, tokenTypeIdsBatch)) {

        inputMap.put("input_ids", inputIdsTensor);
        inputMap.put("attention_mask", attentionMaskTensor);
        inputMap.put("token_type_ids", tokenTypeIdsTensor);

        try (OrtSession.Result result = session.run(inputMap)) {
          float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
          float[] pooled = meanPool(lastHiddenState[0], attentionMask);
          return l2Normalize(pooled);
        }
      }
    } catch (OrtException e) {
      throw new IllegalStateException("ONNX inference failed for input text", e);
    }
  }

  private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
    float[] sum = new float[DIMENSIONS];
    float maskSum = 0.0f;

    for (int i = 0; i < tokenEmbeddings.length; i++) {
      float mask = (float) attentionMask[i];
      if (mask > 0.0f) {
        maskSum += mask;
        for (int d = 0; d < DIMENSIONS; d++) {
          sum[d] += tokenEmbeddings[i][d] * mask;
        }
      }
    }

    if (maskSum > 0.0f) {
      for (int d = 0; d < DIMENSIONS; d++) {
        sum[d] /= maskSum;
      }
    }
    return sum;
  }

  private List<Float> l2Normalize(float[] vector) {
    double sumSq = 0.0;
    for (float v : vector) {
      sumSq += v * v;
    }
    double norm = Math.sqrt(sumSq);
    List<Float> normalized = new ArrayList<>(DIMENSIONS);

    if (norm > 0.0) {
      for (float v : vector) {
        normalized.add((float) (v / norm));
      }
    } else {
      for (float v : vector) {
        normalized.add(v);
      }
    }
    return normalized;
  }

  @Override
  public String getModelVersion() {
    return modelVersion;
  }

  @Override
  public int getDimensions() {
    return DIMENSIONS;
  }

  @PreDestroy
  public void close() {
    try {
      if (session != null) {
        session.close();
      }
      if (environment != null) {
        environment.close();
      }
      if (tempModelFile != null) {
        Files.deleteIfExists(tempModelFile);
      }
    } catch (Exception ignored) {
    }
  }
}