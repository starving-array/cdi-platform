package com.cdi.evidence.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code evidence_record} table (data-model.md §C,
 * V10 migration).
 *
 * <p>Persistence-only mapping of the {@code EvidenceRecord} aggregate:
 * identifiers and scalar immutable state only, no domain logic. Enums are
 * stored as their names (source_type, origin); the Relevance pair is stored as
 * {@code relevance_score}/{@code relevance_reason} (nullable, optional in the
 * domain); the searchable fields are {@code title} (mandatory) and
 * {@code content} (nullable {@code TEXT}). Column names follow the Spring
 * camelCase-to-snake_case strategy and must exactly match the V10 columns
 * (ddl-auto: validate) — {@code content} is declared {@code TEXT} explicitly so
 * the schema validator accepts the nullable text column.
 *
 * <p>The {@code tenant_id} column is the tenant scope of the row
 * (data-model.md §2): deterministic search is always scoped by it and the
 * row-only FK {@code (tenant_id, analysis_run_id)} prevents cross-tenant
 * references (data-model.md §5). No {@code updated_at} is carried — evidence
 * is immutable historical fact and {@code captured_at} is the audit timestamp
 * (V3 precedent).
 */
@Entity
@Table(name = "evidence_record")
public class EvidenceEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private UUID analysisRunId;

  private String sourceType;

  private String sourceUri;

  private String origin;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String content;

  private String contentHash;

  private Instant capturedAt;

  private Instant sourceTimestamp;

  private Double relevanceScore;

  private String relevanceReason;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public UUID getAnalysisRunId() { return analysisRunId; }

  public void setAnalysisRunId(UUID analysisRunId) { this.analysisRunId = analysisRunId; }

  public String getSourceType() { return sourceType; }

  public void setSourceType(String sourceType) { this.sourceType = sourceType; }

  public String getSourceUri() { return sourceUri; }

  public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

  public String getOrigin() { return origin; }

  public void setOrigin(String origin) { this.origin = origin; }

  public String getTitle() { return title; }

  public void setTitle(String title) { this.title = title; }

  public String getContent() { return content; }

  public void setContent(String content) { this.content = content; }

  public String getContentHash() { return contentHash; }

  public void setContentHash(String contentHash) { this.contentHash = contentHash; }

  public Instant getCapturedAt() { return capturedAt; }

  public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

  public Instant getSourceTimestamp() { return sourceTimestamp; }

  public void setSourceTimestamp(Instant sourceTimestamp) { this.sourceTimestamp = sourceTimestamp; }

  public Double getRelevanceScore() { return relevanceScore; }

  public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }

  public String getRelevanceReason() { return relevanceReason; }

  public void setRelevanceReason(String relevanceReason) { this.relevanceReason = relevanceReason; }
}