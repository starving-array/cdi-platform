package com.cdi.common.domain.id;

import java.util.Objects;
import java.util.UUID;

public record DeploymentId(UUID value) {
  public DeploymentId {
    Objects.requireNonNull(value, "DeploymentId value must not be null");
  }

  public static DeploymentId generate() {
    return new DeploymentId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}