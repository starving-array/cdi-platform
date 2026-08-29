package com.cdi.common.adapter.in.security;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CdiJwtAuthenticationConverterTest {

  private CdiJwtAuthenticationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new CdiJwtAuthenticationConverter();
  }

  @Test
  void validJwtWithEngineerRoleMapsCorrectly() {
    UUID tenantUuid = UUID.randomUUID();
    Jwt jwt = createJwt("eng-user-1", tenantUuid.toString(), List.of("ENGINEER"));

    CdiJwtAuthenticationToken token = (CdiJwtAuthenticationToken) converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getTenantId()).isEqualTo(new TenantId(tenantUuid));
    assertThat(token.getActor().id()).isEqualTo("eng-user-1");
    assertThat(token.getActor().role()).isEqualTo(Actor.Role.ENGINEER);
  }

  @Test
  void validJwtWithTenantAdminRoleMapsCorrectly() {
    UUID tenantUuid = UUID.randomUUID();
    Jwt jwt = createJwt("admin-1", tenantUuid.toString(), List.of("TENANT_ADMIN"));

    CdiJwtAuthenticationToken token = (CdiJwtAuthenticationToken) converter.convert(jwt);

    assertThat(token.getActor().role()).isEqualTo(Actor.Role.TENANT_ADMIN);
  }

  @Test
  void validJwtWithSystemWorkerRoleMapsCorrectly() {
    UUID tenantUuid = UUID.randomUUID();
    Jwt jwt = createJwt("worker-ci", tenantUuid.toString(), List.of("SYSTEM_WORKER"));

    CdiJwtAuthenticationToken token = (CdiJwtAuthenticationToken) converter.convert(jwt);

    assertThat(token.getActor().role()).isEqualTo(Actor.Role.SYSTEM_WORKER);
  }

  @Test
  void multipleRolesResolvesWithDeterministicPriority() {
    UUID tenantUuid = UUID.randomUUID();
    // TENANT_ADMIN takes priority over ENGINEER
    Jwt jwt = createJwt("dual-user", tenantUuid.toString(), List.of("ENGINEER", "TENANT_ADMIN"));

    CdiJwtAuthenticationToken token = (CdiJwtAuthenticationToken) converter.convert(jwt);

    assertThat(token.getActor().role()).isEqualTo(Actor.Role.TENANT_ADMIN);
  }

  @Test
  void missingSubjectThrowsUnauthorized() {
    Jwt jwt = createJwt("", UUID.randomUUID().toString(), List.of("ENGINEER"));

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void missingTenantIdThrowsUnauthorized() {
    Jwt jwt = Jwt.withTokenValue("mock")
        .header("alg", "none")
        .subject("user-1")
        .claim("roles", List.of("ENGINEER"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void malformedTenantIdThrowsUnauthorized() {
    Jwt jwt = createJwt("user-1", "not-a-valid-uuid", List.of("ENGINEER"));

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void missingRolesThrowsUnauthorized() {
    Jwt jwt = Jwt.withTokenValue("mock")
        .header("alg", "none")
        .subject("user-1")
        .claim("tenant_id", UUID.randomUUID().toString())
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void unsupportedRoleThrowsUnauthorized() {
    Jwt jwt = createJwt("user-1", UUID.randomUUID().toString(), List.of("UNKNOWN_SUPERUSER"));

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(ApplicationException.class);
  }

  private Jwt createJwt(String sub, String tenantId, List<String> roles) {
    return Jwt.withTokenValue("mock-token")
        .header("alg", "none")
        .subject(sub)
        .claim("tenant_id", tenantId)
        .claim("roles", roles)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }
}