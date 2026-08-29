package com.cdi.common.adapter.in.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@Profile("prod")
@EnableConfigurationProperties(CdiJwtProperties.class)
public class ProductionSecurityConfiguration {

  @Bean
  public SecurityFilterChain productionSecurityFilterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().denyAll()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(new CdiJwtAuthenticationConverter())
            )
        )
        .build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "cdi.security.jwt", name = "jwk-set-uri", matchIfMissing = false)
  public JwtDecoder jwkSetUriJwtDecoder(CdiJwtProperties properties) {
    if (properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()) {
      return null;
    }
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    configureValidators(jwtDecoder, properties);
    return jwtDecoder;
  }

  @Bean
  @ConditionalOnProperty(prefix = "cdi.security.jwt", name = "issuer-uri", matchIfMissing = false)
  public JwtDecoder issuerJwtDecoder(CdiJwtProperties properties) {
    if (properties.issuerUri() == null || properties.issuerUri().isBlank()) {
      return null;
    }
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(properties.issuerUri());
    configureValidators(jwtDecoder, properties);
    return jwtDecoder;
  }

  public static void configureValidators(NimbusJwtDecoder jwtDecoder, CdiJwtProperties properties) {
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    if (properties.issuerUri() != null && !properties.issuerUri().isBlank()) {
      validators.add(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
    } else {
      validators.add(JwtValidators.createDefault());
    }

    if (properties.audiences() != null && !properties.audiences().isEmpty()) {
      validators.add(new AudienceValidator(properties.audiences()));
    }

    jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
  }

  public static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final List<String> requiredAudiences;

    public AudienceValidator(List<String> requiredAudiences) {
      this.requiredAudiences = requiredAudiences;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
      List<String> tokenAud = jwt.getAudience();
      if (tokenAud != null && tokenAud.stream().anyMatch(requiredAudiences::contains)) {
        return OAuth2TokenValidatorResult.success();
      }
      OAuth2Error error = new OAuth2Error("invalid_token", "The required audience is missing or invalid", null);
      return OAuth2TokenValidatorResult.failure(error);
    }
  }
}