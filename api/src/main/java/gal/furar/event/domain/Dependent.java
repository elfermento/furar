// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A persoa a cargo da conta que se apunta (ADR-0031, ADR-0041): a súa ficha e o seu nome, copiado
 * ao apuntala. {@link #toString()} non mostra o nome.
 */
public record Dependent(UUID id, String name) {

  public Dependent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
  }

  @Override
  public String toString() {
    return "Dependent[id=" + id + "]";
  }
}
