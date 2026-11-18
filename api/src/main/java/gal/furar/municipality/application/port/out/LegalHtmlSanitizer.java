// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.out;

import org.jspecify.annotations.Nullable;

/**
 * Sanea o HTML dun texto propio das páxinas legais cunha lista branca estrita (ADR-0017 punto 8):
 * {@code h2}, {@code h3}, {@code p}, {@code strong}, {@code em}, {@code ul}, {@code ol}, {@code
 * li}, {@code br} e {@code a[href]} só con {@code https:} ou {@code mailto:}, que leva {@code
 * rel="noopener noreferrer"}. Ningún outro elemento nin atributo: o texto dos elementos que non
 * están na lista queda, o seu marcado non; o contido de {@code script} e {@code style}, tampouco.
 */
public interface LegalHtmlSanitizer {

  /** O HTML saneado; nulo ou sen texto visible → nulo. */
  @Nullable String sanitize(@Nullable String html);
}
