package com.cdi.common.adapter.in.security;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.common.domain.id.TenantId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Converts a verified Spring Security {@link Jwt} into an authenticated {@link CdiJwtAuthenticationToken}
 * holding strongly-typed {@link TenantId} and {@link Actor}.
 */
public class CdiJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null || sub.isBlank()) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "JWT subject (sub) claim is missing or empty");
    }

    Object tenantClaim = jwt.getClaims().get("tenant_id");
    if (tenantClaim == null || tenantClaim.toString().isBlank()) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "JWT tenant_id claim is missing or empty");
    }

    TenantId tenantId;
    try {
      tenantId = new TenantId(UUID.fromString(tenantClaim.toString().trim()));
    } catch (IllegalArgumentException e) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "JWT tenant_id claim is not a valid UUID: " + tenantClaim);
    }

    Actor.Role actorRole = extractActorRole(jwt);
    Actor actor = new Actor(sub.trim(), actorRole);

    Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + actorRole.name()));
    return new CdiJwtAuthenticationToken(jwt, tenantId, actor, authorities);
  }

  @SuppressWarnings("unchecked")
  private Actor.Role extractActorRole(Jwt jwt) {
    List<String> rawRoles = new ArrayList<>();

    Object rolesClaim = jwt.getClaims().get("roles");
    if (rolesClaim instanceof Collection<?> coll) {
      coll.forEach(item -> {
        if (item != null) {
          rawRoles.add(item.toString());
        }
      });
    } else if (rolesClaim instanceof String str && !str.isBlank()) {
      rawRoles.add(str);
    }

    Object cdiRolesClaim = jwt.getClaims().get("cdi_roles");
    if (cdiRolesClaim instanceof Collection<?> coll) {
      coll.forEach(item -> {
        if (item != null) {
          rawRoles.add(item.toString());
        }
      });
    } else if (cdiRolesClaim instanceof String str && !str.isBlank()) {
      rawRoles.add(str);
    }

    if (rawRoles.isEmpty()) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "JWT roles claim is missing or empty");
    }

    // Role priority order for deterministic selection when multiple roles are present:
    // TENANT_ADMIN > ENGINEER > SYSTEM_WORKER
    List<Actor.Role> parsedRoles = new ArrayList<>();
    for (String raw : rawRoles) {
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      if (normalized.startsWith("ROLE_")) {
        normalized = normalized.substring(5);
      }
      try {
        parsedRoles.add(Actor.Role.valueOf(normalized));
      } catch (IllegalArgumentException e) {
        // Unsupported role string - will be checked below
      }
    }

    if (parsedRoles.isEmpty()) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "JWT contains no supported CDI roles: " + rawRoles);
    }

    if (parsedRoles.contains(Actor.Role.TENANT_ADMIN)) {
      return Actor.Role.TENANT_ADMIN;
    }
    if (parsedRoles.contains(Actor.Role.ENGINEER)) {
      return Actor.Role.ENGINEER;
    }
    return Actor.Role.SYSTEM_WORKER;
  }
}