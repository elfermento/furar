// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Onde se buscan enderezos: o concello actual (especificación: «suxestións do concello»), polo seu
 * nome (sen un cualificativo final entre parénteses), sen maiúsculas nin acentos.
 *
 * @param municipalityName nome do concello; nulo = sen filtro
 */
public record SearchArea(@Nullable String municipalityName) {

  /** Un cualificativo entre parénteses ao final do nome («Narón (demo)»): non é do lugar. */
  private static final Pattern QUALIFIER = Pattern.compile("\\s*\\([^()]*\\)\\s*$");

  /** Sen filtro. */
  public static final SearchArea ANYWHERE = new SearchArea(null);

  /**
   * O nome do lugar: sen un cualificativo final entre parénteses (os concellos de demostración din
   * «(demo)» no nome que se ve, pero os enderezos son os do concello).
   */
  public SearchArea {
    if (municipalityName != null) {
      String place = QUALIFIER.matcher(municipalityName).replaceFirst("");
      municipalityName = place.isBlank() ? municipalityName : place;
    }
  }

  /** Se un enderezo dese concello entra na área. */
  public boolean contains(@Nullable String candidateMunicipality) {
    return municipalityName == null || fold(municipalityName).equals(fold(candidateMunicipality));
  }

  /** Sen maiúsculas nin acentos, para comparar nomes. */
  public static String fold(@Nullable String text) {
    if (text == null) {
      return "";
    }
    String decomposed = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
