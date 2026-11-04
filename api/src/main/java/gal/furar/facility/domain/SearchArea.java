// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.text.Normalizer;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Onde se buscan enderezos: o concello actual (especificación: «suxestións do concello»). Co código
 * INE fíltrase por el; sen INE (aínda non está nos datos legais), polo nome.
 *
 * @param municipalityName nome do concello; nulo = sen filtro
 * @param ineCode código INE; nulo = filtra polo nome
 */
public record SearchArea(@Nullable String municipalityName, @Nullable String ineCode) {

  /** Sen filtro. */
  public static final SearchArea ANYWHERE = new SearchArea(null, null);

  /** Se un enderezo con ese INE e esa localidade/concello entra na área. */
  public boolean contains(@Nullable String candidateIne, @Nullable String candidateMunicipality) {
    if (ineCode != null) {
      return ineCode.equals(candidateIne);
    }
    if (municipalityName != null) {
      return fold(municipalityName).equals(fold(candidateMunicipality));
    }
    return true;
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
