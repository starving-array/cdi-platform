# Semantic Evidence & Neural Retrieval Architecture

---

## 1. Why Semantic Evidence Exists

Engineering teams make changes that often mirror past incidents, bug fixes, or architectural anti-patterns. Traditional keyword search fails when commit messages or incident descriptions use different vocabulary (e.g. "timeout during DB pool exhaustion" vs "HikariCP connection starvation").

Semantic Evidence allows CDI to:
- Retrieve contextually relevant historical evidence (incidents, PRs, architectural notes).
- Ground AI investigations and risk scoring in factual organizational history.
- Prevent duplicate failures by detecting similar modifications before merge.

---

## 2. Embedding Architecture & Port Boundary

To maintain complete independence from external SaaS vendors, rate limits, and network volatility, CDI uses an application-level port:

```
[Evidence Service] ──▶ [EmbeddingPort]
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
[OnnxEmbeddingAdapter]             [DeterministicEmbeddingAdapter]
(Production: all-MiniLM-L6-v2)             (Unit Tests)
```

### Production ONNX Embedding Implementation:
- **Model**: `sentence-transformers/all-MiniLM-L6-v2`
- **Vector Dimensions**: 384
- **Runtime**: Microsoft ONNX Runtime Java (`1.17.1`)
- **Tokenizer**: Pure Java Hugging Face BPE / WordPiece tokenizer
- **Inference**: In-process CPU vectorization with L2 normalization

---

## 3. Storage & Indexing with pgvector

Vectors are persisted in the `evidence_embedding` table:
```sql
CREATE TABLE evidence_embedding (
    id UUID PRIMARY KEY,
    evidence_id UUID NOT NULL REFERENCES evidence_record(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    embedding vector(384) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
```

- **HNSW Index**: Built using `vector_cosine_ops` (`m=16, ef_construction=64`).
- **Cosine Distance Retrieval**: Uses the `<=>` cosine distance operator with similarity calculated as `1.0 - (embedding <=> :queryVector)`.
- **Tenant Scoping**: All vector queries include `WHERE tenant_id = :tenantId`.
- **Threshold Filtering**: Matches below the configurable threshold (default `0.70`) are pruned.
- **Graceful Fallback**: If vector retrieval returns insufficient matches, the query service falls back to deterministic tenant evidence retrieval.

---

## 4. Re-Embedding & Lifecycle Considerations

- **Model Versioning**: Every vector row records `model_version = "all-MiniLM-L6-v2-384-v1"`.
- **Model Replacement**: If a new embedding model is introduced in the future, the `EmbeddingPort` contract guarantees zero domain changes. A background migration can re-embed historical evidence rows incrementally without downtime.