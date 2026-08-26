package com.cdi.common.domain.id;

import java.util.Objects;
import java.util.UUID;

public record AttributionId(UUID value) {
  public AttributionId {
    Objects.requireNonNull(value, "AttributionId value must not be null");
  }

  public static AttributionId generate() {
    return new AttributionId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}