// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Porto para situar un concello no territorio (nome e código INE), sen depender do módulo {@code
 * municipality}: a busca de enderezos de {@code facility} filtra con el as suxestións (ADR-0023).
 * Implementao o módulo {@code municipality}.
 */
public interface MunicipalityPlaces {

  /** Nome e INE do concello; baleiro se non existe. */
  Optional<Place> place(UUID municipalityId);

  /**
   * @param name nome do concello (o mesmo que usa o IGN, p. ex. «Narón»)
   * @param ineCode código INE de 5 díxitos; nulo se o concello aínda non o ten nos datos legais
   */
  record Place(String name, @Nullable String ineCode) {}
}
