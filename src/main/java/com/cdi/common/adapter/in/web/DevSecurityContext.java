package com.cdi.common.adapter.in.web;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.common.domain.id.TenantId;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Development HTTP security context helper (mock authentication).
 * Extracts {@code TenantId} and {@code Actor} from development headers:
 * <ul>
 *   <li>{@code X-Tenant-Id}</li>
 *   <li>{@code X-Actor-Id} (defaults to "dev-user")</li>
 *   <li>{@code X-Actor-Role} (defaults to "ENGINEER")</li>
 * </ul>
 *
 * <p><b>NOTE:</b> This is development/MVP mock authentication only. It enforces
 * strict UUID and Actor.Role parsing and raises standard {@link ApplicationException}
 * on invalid or missing headers.
 */
public final class DevSecurityContext {

  private DevSecurityContext() {}

  public static TenantId extractTenantId(HttpServletRequest request) {
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof com.cdi.common.adapter.in.security.CdiJwtAuthenticationToken cdiToken) {
      return cdiToken.getTenantId();
    }

    String tenantHeader = request.getHeader("X-Tenant-Id");
    if (tenantHeader == null || tenantHeader.isBlank()) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
    try {
      return new TenantId(UUID.fromString(tenantHeader.trim()));
    } catch (IllegalArgumentException e) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  public static Actor extractActor(HttpServletRequest request) {
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof com.cdi.common.adapter.in.security.CdiJwtAuthenticationToken cdiToken) {
      return cdiToken.getActor();
    }

    String actorId = request.getHeader("X-Actor-Id");
    if (actorId == null || actorId.isBlank()) {
      actorId = "dev-user";
    }

    String roleHeader = request.getHeader("X-Actor-Role");
    if (roleHeader == null || roleHeader.isBlank()) {
      roleHeader = "ENGINEER";
    }

    try {
      Actor.Role role = Actor.Role.valueOf(roleHeader.trim().toUpperCase());
      return new Actor(actorId.trim(), role);
    } catch (IllegalArgumentException e) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}