# Architecture Evolution & Future Roadmap

---

## 1. Scope & Intent

This document details how the CDI platform architecture is designed to evolve in future releases without modifying core domain abstractions.

---

## 2. Embedding Model & Vector Evolution

- **Current State**: Local in-process ONNX Runtime Java executing `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions).
- **Future Possibilities**:
  - **Quantized Models**: INT8/FP16 quantized variants for higher vectorization throughput under high concurrency.
  - **Larger Local Models**: e.g. `bge-small-en-v1.5` or `e5-small-v2`.
  - **External Cloud Embedding Adapters**: Pluggable implementations of `EmbeddingPort` for dedicated GPU inference endpoints.
- **Abstraction Guarantee**: The `EmbeddingPort` contract guarantees that swapping embedding engines requires zero changes to the `EvidenceSearchPort` or domain services.

---

## 3. Retrieval Augmentation

- **Current State**: HNSW Cosine Similarity search with configurable thresholds and deterministic fallbacks.
- **Future Possibilities**:
  - **Hybrid Search**: Combining pgvector cosine distance with PostgreSQL full-text search (tsvector/BM25) via Reciprocal Rank Fusion (RRF).
  - **Re-ranking**: Cross-encoder re-ranking stage on top 20 retrieved evidence candidates.

---

## 4. Observational Attribution & Machine Learning Evolution

- **Current State**: Purely observational correlation classifying prediction accuracy (`ACCURATE_LOW_RISK`, `ACCURATE_HIGH_RISK`, `UNDERESTIMATED_RISK`, `OVERESTIMATED_RISK`, `UNATTRIBUTED`). Historical records remain immutable.
- **Future Possibilities**:
  - **Offline Supervised Calibration**: Training calibration curves offline based on attribution telemetry datasets to propose updated versioned rule sets.
  - **Automated Anomaly Highlighting**: Alerting engineering managers when a service experiences a cluster of `UNDERESTIMATED_RISK` deployments.
- **Strict Boundary**: Autonomous runtime risk score mutation without human validation is permanently prohibited to protect audit integrity.