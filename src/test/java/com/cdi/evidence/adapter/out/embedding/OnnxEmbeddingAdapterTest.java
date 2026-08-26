package com.cdi.evidence.adapter.out.embedding;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnnxEmbeddingAdapterTest {

  private static OnnxEmbeddingAdapter adapter;

  @BeforeAll
  static void setUp() {
    adapter = new OnnxEmbeddingAdapter(
        "classpath:models/all-MiniLM-L6-v2/model.onnx",
        "all-MiniLM-L6-v2-onnx-v1",
        new DefaultResourceLoader());
  }

  @AfterAll
  static void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
  }

  @Test
  void modelLoadsSuccessfullyAndReportsDimensions() {
    assertNotNull(adapter);
    assertEquals(384, adapter.getDimensions());
    assertEquals("all-MiniLM-L6-v2-onnx-v1", adapter.getModelVersion());
  }

  @Test
  void generatesNormalized384DimensionalVector() {
    List<Float> vector = adapter.embedText("PostgreSQL connection pool exhaustion caused by leaked sessions");
    assertNotNull(vector);
    assertEquals(384, vector.size());

    // Verify L2 unit norm (~ 1.0)
    double sumSq = 0.0;
    for (float v : vector) {
      sumSq += v * v;
    }
    double norm = Math.sqrt(sumSq);
    assertEquals(1.0, norm, 1e-4);
  }

  @Test
  void handlesEmptyAndBlankText() {
    List<Float> nullVector = adapter.embedText(null);
    assertEquals(384, nullVector.size());
    assertTrue(nullVector.stream().allMatch(v -> v == 0.0f));

    List<Float> blankVector = adapter.embedText("   ");
    assertEquals(384, blankVector.size());
    assertTrue(blankVector.stream().allMatch(v -> v == 0.0f));
  }

  @Test
  void generatesDeterministicOutputForIdenticalInput() {
    String text = "Kafka consumer group partition rebalance failure";
    List<Float> v1 = adapter.embedText(text);
    List<Float> v2 = adapter.embedText(text);

    assertEquals(v1.size(), v2.size());
    for (int i = 0; i < v1.size(); i++) {
      assertEquals(v1.get(i), v2.get(i), 1e-6f);
    }
  }

  @Test
  void performsThreadSafeConcurrentInference() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicBoolean allSucceeded = new AtomicBoolean(true);

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(() -> {
        try {
          List<Float> v = adapter.embedText("Thread execution query " + idx);
          if (v == null || v.size() != 384) {
            allSucceeded.set(false);
          }
        } catch (Exception e) {
          allSucceeded.set(false);
        } finally {
          latch.countDown();
        }
      });
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    executor.shutdown();
    assertTrue(allSucceeded.get());
  }

  @Test
  void missingModelThrowsClearException() {
    assertThrows(IllegalStateException.class, () -> new OnnxEmbeddingAdapter(
        "classpath:models/nonexistent.onnx",
        "v1",
        new DefaultResourceLoader()));
  }
}