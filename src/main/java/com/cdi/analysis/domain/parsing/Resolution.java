package com.cdi.analysis.domain.parsing;

import com.cdi.common.domain.exception.DomainException;

/**
 * How a referenced type was resolved during dependency analysis.
 *
 * <p>REPOSITORY = matches a type declared in one of the files of the analyzed
 * code context (resolved via import, same package, or qualification).
 * EXTERNAL = qualified by an explicit import or fully qualified use but NOT
 * declared in the analyzed files (library/framework/out-of-context code).
 * UNRESOLVED = could not be qualified deterministically; never invented.
 */
public enum Resolution {
  REPOSITORY,
  EXTERNAL,
  UNRESOLVED
}
