// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.port.in;

import java.util.Objects;

/**
 * Os datos dunha ficha, na alta e na edición.
 *
 * @param name nome e apelidos
 * @param birthYear ano de nacemento
 * @param inMyCare a declaración «Esta persoa está ao meu cargo»
 */
public record SaveDependentCommand(String name, int birthYear, boolean inMyCare) {

  public SaveDependentCommand {
    Objects.requireNonNull(name, "name");
  }
}
