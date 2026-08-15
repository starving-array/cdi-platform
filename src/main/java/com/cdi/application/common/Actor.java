package com.cdi.application.common;

import com.cdi.common.domain.exception.DomainException;

/**
 * Resolved actor identity for a use-case invocation (application-layer.md §2,
 * use-cases.md §2).
 *
 * <p>Authentication is handled upstream; the application layer receives this
 * resolved actor and enforces the per-use-case role.
 */
public record Actor(String id, Role role) {

  public enum Role {
    SYSTEM_WORKER,
    TENANT_ADMIN,
    ENGINEER
  }

  public Actor {
    if (id == null || id.isBlank()) {
      throw new DomainException("Actor ID cannot be blank");
    }
    if (role == null) {
      throw new DomainException("Actor role cannot be null");
    }
    id = id.trim();
  }
}