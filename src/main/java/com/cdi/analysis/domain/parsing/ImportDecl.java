package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

/**
 * One Java import statement as found in the parsed source, with its static /
 * wildcard characteristics preserved.
 */
public record ImportDecl(String name, boolean isStatic, boolean isWildcard) {

  public ImportDecl {
    if (name == null || name.isBlank()) {
      throw new DomainException("Import name cannot be blank");
    }
    name = name.trim();
  }
}
