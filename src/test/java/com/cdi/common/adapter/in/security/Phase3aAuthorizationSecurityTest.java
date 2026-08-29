package com.cdi.common.adapter.in.security;

import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.organization.domain.Organization;
import com.cdi.repository.domain.Repository;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import({PostgresTestContainerConfiguration.class, Phase3aAuthorizationSecurityTest.TestJwtConfig.class})
class Phase3aAuthorizationSecurityTest {

  private static final String ISSUER = "https://auth.cdi.example.com";
  private static final String AUDIENCE = "cdi-platform-api";

  private static RSAKey rsaJwk;
  private static KeyPair keyPair;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private DeploymentRepository deploymentRepository;

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
  void tenantAdminCanRetrieveChangeDetails() throws Exception {
    TenantId tenantId = seedTenant();
    RepositoryId repoId = seedRepository(tenantId, "repo-admin-change");
    Change change = new Change(
        ChangeId.generate(), repoId, "PR-100", "Admin readable change",
        "Description", "author", "feature", "main", "sha-admin-1", Instant.now());
    changeRepository.save(tenantId, change);

    String adminToken = createToken(ISSUER, AUDIENCE, tenantId.value(), "admin-user", List.of("TENANT_ADMIN"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/changes/{id}", change.getId().value())
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.change.title").value("Admin readable change"));
  }

  @Test
  void tenantAdminCanSearchEvidence() throws Exception {
    TenantId tenantId = seedTenant();

    String adminToken = createToken(ISSUER, AUDIENCE, tenantId.value(), "admin-user", List.of("TENANT_ADMIN"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/evidence/search")
            .header("Authorization", "Bearer " + adminToken)
            .param("query", "production leak")
            .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.degraded").value(false));
  }

  @Test
  void systemWorkerCannotListChanges() throws Exception {
    TenantId tenantId = seedTenant();

    String workerToken = createToken(ISSUER, AUDIENCE, tenantId.value(), "worker-ci", List.of("SYSTEM_WORKER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/changes")
            .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void systemWorkerCannotSearchEvidence() throws Exception {
    TenantId tenantId = seedTenant();

    String workerToken = createToken(ISSUER, AUDIENCE, tenantId.value(), "worker-ci", List.of("SYSTEM_WORKER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    mockMvc.perform(get("/api/v1/evidence/search")
            .header("Authorization", "Bearer " + workerToken)
            .param("query", "deployment"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void tenantAEngineerCannotAccessTenantBChangeByUuid() throws Exception {
    TenantId tenantA = seedTenant();
    TenantId tenantB = seedTenant();

    RepositoryId repoB = seedRepository(tenantB, "repo-b-change");
    Change changeB = new Change(
        ChangeId.generate(), repoB, "PR-B", "Tenant B Confidential Change",
        "Description", "bob", "feature", "main", "sha-b", Instant.now());
    changeRepository.save(tenantB, changeB);

    String engineerTokenA = createToken(ISSUER, AUDIENCE, tenantA.value(), "alice", List.of("ENGINEER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // When Tenant A engineer requests Tenant B change ID, return 404 CHANGE_NOT_FOUND (IDOR safe)
    mockMvc.perform(get("/api/v1/changes/{id}", changeB.getId().value())
            .header("Authorization", "Bearer " + engineerTokenA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CHANGE_NOT_FOUND"));
  }

  @Test
  void tenantASystemWorkerCannotMutateTenantBDeploymentOutcome() throws Exception {
    TenantId tenantA = seedTenant();
    TenantId tenantB = seedTenant();

    Service serviceB = seedService(tenantB, "billing-service-b");
    Deployment deploymentB = new Deployment(
        DeploymentId.generate(), tenantB, serviceB.getId(), "sha-b-dep", "production",
        DeploymentStatus.IN_PROGRESS, "ext-b-1", Instant.now(), Instant.now());
    deploymentRepository.save(deploymentB);

    String workerTokenA = createToken(ISSUER, AUDIENCE, tenantA.value(), "worker-a", List.of("SYSTEM_WORKER"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // When Tenant A worker attempts to record outcome on Tenant B deployment, return 404 DEPLOYMENT_NOT_FOUND
    mockMvc.perform(post("/api/v1/deployments/{id}/outcomes", deploymentB.getId().value())
            .header("Authorization", "Bearer " + workerTokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "outcome": "SUCCESS"
                }
                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEPLOYMENT_NOT_FOUND"));
  }

  @Test
  void tenantATenantAdminCannotAccessTenantBAttribution() throws Exception {
    TenantId tenantA = seedTenant();
    TenantId tenantB = seedTenant();

    Service serviceB = seedService(tenantB, "payment-service-b");
    Deployment deploymentB = new Deployment(
        DeploymentId.generate(), tenantB, serviceB.getId(), "sha-b-attr", "production",
        DeploymentStatus.IN_PROGRESS, "ext-b-attr", Instant.now(), Instant.now());
    deploymentRepository.save(deploymentB);

    String adminTokenA = createToken(ISSUER, AUDIENCE, tenantA.value(), "admin-a", List.of("TENANT_ADMIN"),
        Date.from(Instant.now().plusSeconds(3600)), Date.from(Instant.now()));

    // When Tenant A admin requests Tenant B attribution, return 404 (DEPLOYMENT_NOT_FOUND or ATTRIBUTION_NOT_FOUND)
    mockMvc.perform(get("/api/v1/attribution/{deploymentId}", deploymentB.getId().value())
            .header("Authorization", "Bearer " + adminTokenA))
        .andExpect(status().isNotFound());
  }

  private TenantId seedTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(tenantId, "Org " + tenantId.value(), Instant.now()));
    return tenantId;
  }

  private RepositoryId seedRepository(TenantId tenantId, String name) {
    RepositoryId repoId = RepositoryId.generate();
    repositoryRepository.save(new Repository(
        repoId, tenantId, Repository.ProviderType.GITHUB, "ext-" + UUID.randomUUID(),
        name, "https://github.com/example/" + name, "main", Instant.now()));
    return repoId;
  }

  private Service seedService(TenantId tenantId, String name) {
    Service service = new Service(
        ServiceId.generate(), tenantId, name, CriticalityTier.TIER_1, "team-core", Instant.now());
    return serviceRepository.save(service);
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