package com.cdi.evidence.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing the pgvector embedding for an evidence record (data-model.md §3.C).
 */
@Entity
@Table(name = "evidence_embedding")
public class EvidenceEmbeddingEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "evidence_record_id", nullable = false)
  private UUID evidenceRecordId;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Column(name = "model_version", nullable = false, length = 64)
  private String modelVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EvidenceEmbeddingEntity() {}

  public EvidenceEmbeddingEntity(
      UUID id,
      UUID tenantId,
      UUID evidenceRecordId,
      int chunkIndex,
      String modelVersion,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.evidenceRecordId = evidenceRecordId;
    this.chunkIndex = chunkIndex;
    this.modelVersion = modelVersion;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getEvidenceRecordId() { return evidenceRecordId; }
  public int getChunkIndex() { return chunkIndex; }
  public String getModelVersion() { return modelVersion; }
  public Instant getCreatedAt() { return createdAt; }
}