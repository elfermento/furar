// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Locale;

/**
 * A única normalización dun correo para comparalo (ADR-0031, punto 4): sen espazos ao redor e en
 * minúsculas con {@link Locale#ROOT}. Úsana o persoal (o correo invitado contra o da sesión), as
 * contas coñecidas polo correo e as reservas (de quen é unha reserva, a praza do titular e o límite
 * por persoa). A base non normaliza por si: {@code lower(btrim())} non dá sempre o mesmo.
 */
public final class EmailKey {

  private EmailKey() {}

  /** A clave do correo: {@code strip()} e minúsculas. */
  public static String of(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
