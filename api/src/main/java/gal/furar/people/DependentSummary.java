// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import java.util.Objects;
import java.util.UUID;

/**
 * Unha persoa a cargo activa, para outros módulos (ADR-0031, punto 3): o que copia unha reserva.
 *
 * @param name nome e apelidos
 * @param birthYear ano de nacemento
 */
public record DependentSummary(UUID id, String name, int birthYear) {

  public DependentSummary {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
  }
}
