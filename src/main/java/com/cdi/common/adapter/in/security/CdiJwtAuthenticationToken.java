package com.cdi.common.adapter.in.security;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.id.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Specialized {@link JwtAuthenticationToken} holding resolved and strongly-typed
 * {@link TenantId} and {@link Actor}.
 */
public class CdiJwtAuthenticationToken extends JwtAuthenticationToken {

  private final TenantId tenantId;
  private final Actor actor;

  public CdiJwtAuthenticationToken(
      Jwt jwt,
      TenantId tenantId,
      Actor actor,
      Collection<? extends GrantedAuthority> authorities) {
    super(jwt, authorities, actor.id());
    this.tenantId = tenantId;
    this.actor = actor;
  }

  public TenantId getTenantId() {
    return tenantId;
  }

  public Actor getActor() {
    return actor;
  }
}