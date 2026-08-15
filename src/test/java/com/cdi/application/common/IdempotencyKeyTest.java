package com.cdi.application.common;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyKeyTest {

  @Test
  void shouldCreateValidKey() {
    IdempotencyKey key = new IdempotencyKey("hash(tenant,repo,pr,commit)");

    assertEquals("hash(tenant,repo,pr,commit)", key.key());
  }

  @Test
  void shouldTrimKey() {
    IdempotencyKey key = new IdempotencyKey("  abc  ");

    assertEquals("abc", key.key());
  }

  @Test
  void shouldRejectBlankKey() {
    assertThrows(DomainException.class, () -> new IdempotencyKey("   "));
    assertThrows(DomainException.class, () -> new IdempotencyKey(null));
  }
}