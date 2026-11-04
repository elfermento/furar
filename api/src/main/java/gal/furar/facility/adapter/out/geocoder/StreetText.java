// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import java.util.Locale;
import java.util.Set;

/**
 * O rueiro do IGN vén en maiúsculas («RUA CONCEPCION ARENAL 100, Narón»). Para o xestor pásase a
 * maiúscula inicial («Rua Concepcion Arenal 100, Narón»), coas partículas en minúscula; o que xa
 * trae minúsculas («Narón») queda como vén. O xestor pode corrixilo antes de gardar.
 */
final class StreetText {

  private static final Locale LOCALE = Locale.of("gl", "ES");
  private static final Set<String> PARTICLES =
      Set.of(
          "a", "o", "as", "os", "da", "do", "das", "dos", "de", "del", "la", "las", "los", "el",
          "e", "y");

  private StreetText() {}

  static String pretty(String text) {
    String[] words = text.strip().split(" +");
    StringBuilder out = new StringBuilder(text.length());
    boolean startOfPhrase = true;
    for (String word : words) {
      if (!out.isEmpty()) {
        out.append(' ');
      }
      out.append(isUpperCase(word) ? capitalize(word, startOfPhrase) : word);
      startOfPhrase = word.endsWith(",") || word.endsWith("(") || word.endsWith("[");
    }
    return out.toString();
  }

  private static boolean isUpperCase(String word) {
    boolean letters = false;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if (Character.isLowerCase(c)) {
        return false;
      }
      letters |= Character.isLetter(c);
    }
    return letters;
  }

  private static String capitalize(String word, boolean startOfPhrase) {
    String lower = word.toLowerCase(LOCALE);
    int start = 0;
    while (start < lower.length() && !Character.isLetter(lower.charAt(start))) {
      start++;
    }
    String bare = lower.substring(start).replaceAll("[^\\p{L}]+$", "");
    if (!startOfPhrase && start == 0 && PARTICLES.contains(bare)) {
      return lower;
    }
    return lower.substring(0, start)
        + lower.substring(start, start + 1).toUpperCase(LOCALE)
        + lower.substring(start + 1);
  }
}
