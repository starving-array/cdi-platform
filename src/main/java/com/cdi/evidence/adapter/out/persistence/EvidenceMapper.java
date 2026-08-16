package com.cdi.evidence.adapter.out.persistence;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.Relevance;
import com.cdi.evidence.domain.SourceType;

/**
 * Maps between the {@code EvidenceRecord} aggregate and its persistence
 * representation (V10, T1-approved mapping).
 *
 * <p>The {@code EvidenceRecord} carries its own {@code TenantId} directly, so
 * the conversion needs no separate {@code tenantId} argument. Enums become
 * their names ([source_type, origin]); the optional {@code Relevance} pair
 * flattens to {@code relevance_score}/{@code relevance_reason}; on read the
 * aggregate is rebuilt via the {@link EvidenceRecord.Builder} (the content
 * hash is recomputed by the builder from content and stored as-is on write).
 */
final class EvidenceMapper {

  private EvidenceMapper() {
    // utility class
  }

  static EvidenceEntity toEntity(EvidenceRecord record) {
    EvidenceEntity entity = new EvidenceEntity();
    entity.setId(record.getId().value());
    entity.setTenantId(record.getTenantId().value());
    entity.setAnalysisRunId(record.getAnalysisRunId().value());
    entity.setSourceType(record.getSource().sourceType().name());
    entity.setSourceUri(record.getSource().sourceReference());
    entity.setOrigin(record.getOrigin().name());
    entity.setTitle(record.getTitle());
    entity.setContent(record.getContent().orElse(null));
    entity.setContentHash(record.getContentHash().orElse(null));
    entity.setCapturedAt(record.getCapturedAt());
    entity.setSourceTimestamp(record.getSourceTimestamp().orElse(null));
    entity.setRelevanceScore(record.getRelevance().map(Relevance::score).orElse(null));
    entity.setRelevanceReason(record.getRelevance().map(Relevance::reason).orElse(null));
    return entity;
  }

  static EvidenceRecord toDomain(EvidenceEntity entity) {
    return EvidenceRecord.builder()
        .id(new EvidenceId(entity.getId()))
        .tenantId(new TenantId(entity.getTenantId()))
        .analysisRunId(new AnalysisRunId(entity.getAnalysisRunId()))
        .source(new EvidenceSource(SourceType.valueOf(entity.getSourceType()), entity.getSourceUri()))
        .origin(EvidenceOrigin.valueOf(entity.getOrigin()))
        .title(entity.getTitle())
        .content(entity.getContent())
        .capturedAt(entity.getCapturedAt())
        .sourceTimestamp(entity.getSourceTimestamp())
        .relevance(toRelevance(entity))
        .build();
  }

  private static Relevance toRelevance(EvidenceEntity entity) {
    if (entity.getRelevanceScore() == null && entity.getRelevanceReason() == null) {
      return null;
    }
    return new Relevance(entity.getRelevanceScore(), entity.getRelevanceReason());
  }
}