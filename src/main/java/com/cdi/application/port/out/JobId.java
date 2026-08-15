package com.cdi.application.port.out;

import com.cdi.common.domain.exception.DomainException;

/**
 * Opaque handle to a queued background job returned by
 * {@link JobQueuePort#enqueue}.
 */
public record JobId(String value) {

  public JobId {
    if (value == null || value.isBlank()) {
      throw new DomainException("Job ID cannot be blank");
    }
    value = value.trim();
  }
}