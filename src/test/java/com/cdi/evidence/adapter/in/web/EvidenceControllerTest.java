package com.cdi.evidence.adapter.in.web;

import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.evidence.SearchEvidenceQueryService;
import com.cdi.application.evidence.SearchEvidenceResult;
import com.cdi.common.adapter.in.web.DevCorsConfiguration;
import com.cdi.common.adapter.in.web.RestApiExceptionHandler;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceController.class)
@Import({RestApiExceptionHandler.class, DevCorsConfiguration.class})
class EvidenceControllerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private SearchEvidenceQueryService searchEvidenceQueryService;

  @Test
  void successfulSearchReturnsEvidenceList() throws Exception {
    UUID tenantId = UUID.randomUUID();
    EvidenceRecord rec = EvidenceRecord.builder()
        .id(EvidenceId.generate())
        .tenantId(new com.cdi.common.domain.id.TenantId(tenantId))
        .analysisRunId(com.cdi.common.domain.id.AnalysisRunId.generate())
        .source(new EvidenceSource(SourceType.INCIDENT, "inc-101"))
        .origin(EvidenceOrigin.RETRIEVED)
        .title("INC-101 Outage")
        .content("Outage caused by database connection leak")
        .capturedAt(NOW)
        .build();

    SearchEvidenceResult result = new SearchEvidenceResult(false, List.of(rec));

    when(searchEvidenceQueryService.handle(any(), eq(new com.cdi.common.domain.id.TenantId(tenantId)), any()))
        .thenReturn(result);

    mockMvc.perform(get("/api/v1/evidence/search")
            .header("X-Tenant-Id", tenantId.toString())
            .param("query", "database leak")
            .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.degraded").value(false))
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].title").value("INC-101 Outage"))
        .andExpect(jsonPath("$.results[0].origin").value("RETRIEVED"));
  }

  @Test
  void degradedSearchReturnsDegradedFlagTrue() throws Exception {
    UUID tenantId = UUID.randomUUID();
    SearchEvidenceResult result = new SearchEvidenceResult(true, List.of());

    when(searchEvidenceQueryService.handle(any(), eq(new com.cdi.common.domain.id.TenantId(tenantId)), any()))
        .thenReturn(result);

    mockMvc.perform(get("/api/v1/evidence/search")
            .header("X-Tenant-Id", tenantId.toString())
            .param("query", "timeout query"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.degraded").value(true))
        .andExpect(jsonPath("$.results.length()").value(0));
  }

  @Test
  void unauthorizedRoleRejected() throws Exception {
    UUID tenantId = UUID.randomUUID();
    when(searchEvidenceQueryService.handle(any(), any(), any()))
        .thenThrow(new ApplicationException(ApplicationError.UNAUTHORIZED));

    mockMvc.perform(get("/api/v1/evidence/search")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Role", "SYSTEM_WORKER")
            .param("query", "test"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}