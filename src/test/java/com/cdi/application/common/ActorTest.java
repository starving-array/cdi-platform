package com.cdi.application.common;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorTest {

  @Test
  void shouldCreateValidResolvedActor() {
    Actor actor = new Actor("user-42", Actor.Role.ENGINEER);

    assertEquals("user-42", actor.id());
    assertEquals(Actor.Role.ENGINEER, actor.role());
  }

  @Test
  void shouldRejectBlankId() {
    assertThrows(DomainException.class, () -> new Actor("  ", Actor.Role.ENGINEER));
  }

  @Test
  void shouldRejectNullRole() {
    assertThrows(DomainException.class, () -> new Actor("user-42", null));
  }
}