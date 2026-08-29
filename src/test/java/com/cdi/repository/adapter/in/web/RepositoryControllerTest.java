package com.cdi.repository.adapter.in.web;

import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreateRepositoryResult;
import com.cdi.application.port.in.CreateRepositoryCommand;
import com.cdi.application.repository.CreateRepositoryHandler;
import com.cdi.common.adapter.in.web.DevCorsConfiguration;
import com.cdi.common.adapter.in.web.RestApiExceptionHandler;
import com.cdi.common.domain.id.RepositoryId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepositoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RestApiExceptionHandler.class, DevCorsConfiguration.class})
class RepositoryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private CreateRepositoryHandler createRepositoryHandler;

  @Test
  void createRepository_whenTenantAdmin_returnsCreated() throws Exception {
    RepositoryId repoId = RepositoryId.generate();
    when(createRepositoryHandler.handle(any(CreateRepositoryCommand.class)))
        .thenReturn(new CreateRepositoryResult(repoId, true));

    RepositoryWebDtos.CreateRepositoryRequest request = new RepositoryWebDtos.CreateRepositoryRequest(
        "GITHUB", "demo-repo", "demo-repo", "https://github.com/example/demo-repo", "main"
    );

    mockMvc.perform(post("/api/v1/repositories")
        .header("X-Tenant-Id", UUID.randomUUID().toString())
        .header("X-Actor-Id", UUID.randomUUID().toString())
        .header("X-Actor-Role", "TENANT_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.repositoryId").value(repoId.value().toString()))
        .andExpect(jsonPath("$.created").value(true));
  }

  @Test
  void createRepository_whenDuplicate_returnsOkAndCreatedFalse() throws Exception {
    RepositoryId repoId = RepositoryId.generate();
    when(createRepositoryHandler.handle(any(CreateRepositoryCommand.class)))
        .thenReturn(new CreateRepositoryResult(repoId, false));

    RepositoryWebDtos.CreateRepositoryRequest request = new RepositoryWebDtos.CreateRepositoryRequest(
        "GITHUB", "demo-repo", "demo-repo", "https://github.com/example/demo-repo", "main"
    );

    mockMvc.perform(post("/api/v1/repositories")
        .header("X-Tenant-Id", UUID.randomUUID().toString())
        .header("X-Actor-Id", UUID.randomUUID().toString())
        .header("X-Actor-Role", "TENANT_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryId").value(repoId.value().toString()))
        .andExpect(jsonPath("$.created").value(false));
  }

  @Test
  void createRepository_whenNotTenantAdmin_returnsForbidden() throws Exception {
    when(createRepositoryHandler.handle(any(CreateRepositoryCommand.class)))
        .thenThrow(new ApplicationException(ApplicationError.UNAUTHORIZED));

    RepositoryWebDtos.CreateRepositoryRequest request = new RepositoryWebDtos.CreateRepositoryRequest(
        "GITHUB", "demo-repo", "demo-repo", "https://github.com/example/demo-repo", "main"
    );

    mockMvc.perform(post("/api/v1/repositories")
        .header("X-Tenant-Id", UUID.randomUUID().toString())
        .header("X-Actor-Id", UUID.randomUUID().toString())
        .header("X-Actor-Role", "ENGINEER")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }
}
