package com.cdi.analysis.domain.parsing;

import java.util.List;

/**
 * One Java type (class/interface/enum/record/annotation) discovered in a
 * changed file, with its members and whether the change touched it.
 */
public record TypeChange(
    String name,
    Kind kind,
    boolean nested,
    int beginLine,
    int endLine,
    boolean changed,
    List<String> imports,
    List<String> fields,
    List<MemberChange> methods,
    List<MemberChange> constructors) {

  public enum Kind {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION
  }

  public TypeChange {
    imports = imports == null ? List.of() : List.copyOf(imports);
    fields = fields == null ? List.of() : List.copyOf(fields);
    methods = methods == null ? List.of() : List.copyOf(methods);
    constructors = constructors == null ? List.of() : List.copyOf(constructors);
  }
}
