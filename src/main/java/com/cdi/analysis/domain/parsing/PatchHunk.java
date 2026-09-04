package com.cdi.analysis.domain.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One unified-diff hunk: the old- and new-side line ranges.
 *
 * <p>Parsed deterministically from the {@code @@ -old[,n] +new[,n] @@} header
 * lines of a GitHub patch. Counts default to 1 when omitted, matching
 * unified-diff semantics.
 */
public record PatchHunk(int oldStart, int oldLength, int newStart, int newLength) {

  private static final Pattern HUNK_HEADER =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

  /**
   * Parses all hunk headers in a patch. An absent/empty patch yields an empty
   * list; malformed lines are skipped (never crash analysis on partial diffs).
   */
  public static List<PatchHunk> parse(String patch) {
    List<PatchHunk> hunks = new ArrayList<>();
    if (patch == null || patch.isBlank()) {
      return hunks;
    }
    for (String line : patch.split("\n", -1)) {
      Matcher m = HUNK_HEADER.matcher(line);
      if (m.find()) {
        int oldStart = Integer.parseInt(m.group(1));
        int oldLen = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
        int newStart = Integer.parseInt(m.group(3));
        int newLen = m.group(4) != null ? Integer.parseInt(m.group(4)) : 1;
        hunks.add(new PatchHunk(oldStart, oldLen, newStart, newLen));
      }
    }
    return hunks;
  }
}
