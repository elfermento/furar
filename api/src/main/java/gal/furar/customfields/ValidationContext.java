// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.Audience;
import java.util.Objects;

/**
 * Contexto dunha escritura de campos personalizados.
 *
 * @param writer audiencia de quen escribe: só pode escribir os campos que ve
 */
public record ValidationContext(Audience writer) {

  public ValidationContext {
    Objects.requireNonNull(writer, "writer");
  }

  public static ValidationContext of(Audience writer) {
    return new ValidationContext(writer);
  }
}
