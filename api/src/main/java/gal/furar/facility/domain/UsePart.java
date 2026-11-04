// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Parte con nome dun uso por partes («Campo A», «Lonxitude»). A súa posición é a da lista do uso.
 * Ao quitala arquívase (as reservas do dominio 2 apuntan a ela).
 */
public record UsePart(UUID id, String name) {

  public UsePart {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
  }
}
