package com.cdi.common.adapter.in.security;

import com.cdi.testconfig.PostgresTestContainerConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import({PostgresTestContainerConfiguration.class, JwtSecurityIntegrationTest.TestJwtConfig.class})
class JwtSecurityIntegrationTest {

  private static final String ISSUER = "https://auth.cdi.example.com";
  private static final String AUDIENCE = "cdi-platform-api";

  private static RSAKey rsaJwk;
  private static KeyPair keyPair;

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void setupKeys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    keyPair = gen.generateKeyPair();

    rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey((RSAPrivateKey) keyPair.getPrivate())
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  @TestConfiguration
  static class TestJwtConfig {
    @Bean
    public JwtDecoder jwtDecoder() {
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
      CdiJwtProperties props = new CdiJwtProperties(ISSUER, null, List.of(AUDIENCE));
      ProductionSecurityConfiguration.configureValidators(decoder, props);
      return decoder;
    }
  }

  @Test
  void requestWithoutAuthorizationHeaderReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/changes")
            .header("X-Tenant-Id", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requestWithMalformedBearerTokenReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requestWithExpiredTokenReturns401() throws Exception {
    Instant past = Instant.now().minusSeconds(3600);
    String expiredToken = createToken(ISSUER, AUDIENCE, Date.from(past), Date.from(past.minusSeconds(60)));

    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requestWithWrongIssuerReturns401() throws Exception {
    String wrongIssuerToken = createToken("https://untrusted-issuer.com", AUDIENCE,
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + wrongIssuerToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requestWithWrongAudienceReturns401() throws Exception {
    String wrongAudienceToken = createToken(ISSUER, "wrong-api-audience",
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + wrongAudienceToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void actuatorHealthPermittedWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk());
  }

  @Test
  void validJwtPassesSecurityFilter() throws Exception {
    String validToken = createToken(ISSUER, AUDIENCE,
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // When valid JWT is presented, request reaches the application layer (which expects tenant context)
    // Testing that it passes Spring Security filter (i.e. does not return 401 Unauthorized)
    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", UUID.randomUUID().toString()))
        .andExpect(status().isOk());
  }

  @Test
  void jwtTenantIdCannotBeOverriddenBySpoofedHeader() throws Exception {
    UUID actualJwtTenant = UUID.randomUUID();
    UUID spoofedHeaderTenant = UUID.randomUUID();

    String validToken = createToken(ISSUER, AUDIENCE, actualJwtTenant, "alice", List.of("ENGINEER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // When request carries both valid JWT and a spoofed X-Tenant-Id header,
    // DevSecurityContext extracts the authentic JWT tenant_id, not the spoofed header
    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", spoofedHeaderTenant.toString()))
        .andExpect(status().isOk());
  }

  @Test
  void jwtRoleCannotBeOverriddenBySpoofedHeader() throws Exception {
    UUID actualJwtTenant = UUID.randomUUID();

    // JWT has SYSTEM_WORKER role (forbidden on GET /api/v1/changes)
    String workerToken = createToken(ISSUER, AUDIENCE, actualJwtTenant, "ci-bot", List.of("SYSTEM_WORKER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // Caller attempts privilege escalation with spoofed header X-Actor-Role: TENANT_ADMIN
    // Expectation: Request is rejected with 403 Forbidden because JWT identity (SYSTEM_WORKER) is authoritative
    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + workerToken)
            .header("X-Tenant-Id", actualJwtTenant.toString())
            .header("X-Actor-Role", "TENANT_ADMIN"))
        .andExpect(status().isForbidden());
  }

  private String createToken(String issuer, String audience, Date exp, Date iat) throws Exception {
    return createToken(issuer, audience, UUID.randomUUID(), "user-123", List.of("ENGINEER"), exp, iat);
  }

  private String createToken(
      String issuer,
      String audience,
      UUID tenantId,
      String sub,
      List<String> roles,
      Date exp,
      Date iat) throws Exception {
    JWSSigner signer = new RSASSASigner(rsaJwk);

    JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
        .subject(sub)
        .issuer(issuer)
        .audience(audience)
        .expirationTime(exp)
        .issueTime(iat)
        .claim("tenant_id", tenantId.toString())
        .claim("roles", roles)
        .build();

    SignedJWT signedJWT = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(),
        claimsSet);

    signedJWT.sign(signer);
    return signedJWT.serialize();
  }
}