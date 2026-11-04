// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.Language;
import java.util.Objects;

/**
 * Quen reserva, copiado do token ao reservar (ADR-0025, punto 3): {@code sub}, nome, correo e
 * idioma dos avisos. Sen teléfono (P7). Datos persoais: conservación de ADR-0017.
 *
 * @param subject {@code sub} de Keycloak: filtra «as miñas reservas»
 * @param name nome para o mostrador e o correo
 * @param email correo verificado
 * @param language idioma dos correos
 */
public record Holder(String subject, String name, String email, Language language) {

  public Holder {
    Objects.requireNonNull(language, "language");
    check("subject", subject, 1, 255);
    check("name", name, 1, 200);
    check("email", email, 3, 254);
    if (!email.matches("^.+@.+$")) {
      throw new IllegalArgumentException("Invalid email");
    }
  }

  private static void check(String field, String value, int min, int max) {
    if (value == null || value.length() < min || value.length() > max) {
      throw new IllegalArgumentException(field + " length out of range");
    }
  }
}
