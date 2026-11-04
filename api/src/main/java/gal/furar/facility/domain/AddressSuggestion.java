// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;

/**
 * Unha suxestión do buscador de enderezos (ADR-0023).
 *
 * @param id identificador opaco para {@code resolve}; só o entende o buscador que o deu
 * @param label texto que ve o xestor («Rúa Real 10, Ferrol»)
 */
public record AddressSuggestion(String id, String label) {

  public AddressSuggestion {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
  }
}
