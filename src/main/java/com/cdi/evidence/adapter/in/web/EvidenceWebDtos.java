package com.cdi.evidence.adapter.in.web;

import com.cdi.application.evidence.SearchEvidenceResult;
import com.cdi.evidence.domain.EvidenceRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvidenceWebDtos {

  private EvidenceWebDtos() {}

  public record EvidenceRecordResponse(
      UUID id,
      UUID analysisRunId,
      String source,
      String origin,
      String title,
      String content,
      Instant capturedAt
  ) {
    public static EvidenceRecordResponse fromDomain(EvidenceRecord record) {
      return new EvidenceRecordResponse(
          record.getId().value(),
          record.getAnalysisRunId().value(),
          record.getSource().sourceType().name(),
          record.getOrigin().name(),
          record.getTitle(),
          record.getContent().orElse(""),
          record.getCapturedAt()
      );
    }
  }

  public record SearchEvidenceResponse(
      boolean degraded,
      List<EvidenceRecordResponse> results
  ) {
    public static SearchEvidenceResponse fromResult(SearchEvidenceResult result) {
      List<EvidenceRecordResponse> list = result.records().stream()
          .map(EvidenceRecordResponse::fromDomain)
          .toList();
      return new SearchEvidenceResponse(result.degraded(), list);
    }
  }
}