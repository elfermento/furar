// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «de» + nome dun lugar, como se escribe en cada idioma. En galego a preposición contrae co artigo
 * do topónimo: «da Coruña», «do Porriño», «das Pontes», «dos Blancos»; o resto, «de Narón». En
 * castelán sempre «de» («de A Coruña»). A mesma regra ca a web ({@code shared/i18n/place-name.ts}).
 *
 * <p>O nome do concello é o topónimo («Narón»), e os textos engaden «Concello»; se alguén escribiu
 * a denominación («Concello de Narón», «Ayuntamiento de A Coruña»), úsase o topónimo, para non
 * dicir «Concello de Concello de Narón».
 */
public final class PlaceName {

  private static final List<String[]> GALICIAN_ARTICLES =
      List.of(
          new String[] {"As ", "das "},
          new String[] {"Os ", "dos "},
          new String[] {"A ", "da "},
          new String[] {"O ", "do "});

  /** «Concello de …», «Ayuntamiento da …»: a institución, a preposición e o resto. */
  private static final Pattern INSTITUTION =
      Pattern.compile(
          "^(?:concello|ayuntamiento)\\s+(de|da|do|das|dos)\\s+(\\S.*)$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** O artigo que leva o topónimo segundo a preposición contraída. */
  private static final Map<String, String> ARTICLE =
      Map.of("de", "", "da", "A ", "do", "O ", "das", "As ", "dos", "Os ");

  private PlaceName() {}

  /** «de Narón», «da Coruña»… (o nome, sen espazos nos extremos). */
  public static String of(String name, Language language) {
    String place = toponym(name);
    if (language == Language.GL) {
      for (String[] article : GALICIAN_ARTICLES) {
        if (place.startsWith(article[0]) && place.length() > article[0].length()) {
          return article[1] + place.substring(article[0].length());
        }
      }
    }
    return "de " + place;
  }

  /** O topónimo: «Concello da Coruña» → «A Coruña»; «Narón» → «Narón». */
  static String toponym(String name) {
    String trimmed = name.strip();
    Matcher institution = INSTITUTION.matcher(trimmed);
    if (!institution.matches()) {
      return trimmed;
    }
    return ARTICLE.get(institution.group(1).toLowerCase(Locale.ROOT)) + institution.group(2);
  }
}
