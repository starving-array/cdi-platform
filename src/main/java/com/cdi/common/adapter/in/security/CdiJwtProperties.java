package com.cdi.common.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cdi.security.jwt")
public record CdiJwtProperties(
    String issuerUri,
    String jwkSetUri,
    List<String> audiences) {

  public CdiJwtProperties {
    audiences = (audiences == null || audiences.isEmpty()) ? List.of("cdi-platform-api") : List.copyOf(audiences);
  }
}