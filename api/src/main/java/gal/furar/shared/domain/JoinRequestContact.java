// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A quen chegan as solicitudes de alta dos concellos («¿Es un concello?», ADR-0042). Sae da
 * configuración ({@code FURAR_JOIN_REQUEST_EMAIL}) e nunca vai á web: a web só sabe se a función
 * existe. Opcional: baleiro, a función non existe (nin franxa, nin ligazón, nin ruta; a API
 * responde 404). Un valor que non é un correo impide arrincar.
 *
 * @param email correo de quen opera o servizo, sen espazos ao redor; baleiro se non hai
 */
public record JoinRequestContact(Optional<String> email) {

  public JoinRequestContact {
    Objects.requireNonNull(email, "email");
    email.ifPresent(JoinRequestContact::check);
  }

  /**
   * Desde o valor de contorno.
   *
   * @param email o correo; {@code null}, baleiro ou só espazos = sen solicitudes de alta
   */
  public static JoinRequestContact parse(String email) {
    if (email == null || email.isBlank()) {
      return new JoinRequestContact(Optional.empty());
    }
    return new JoinRequestContact(Optional.of(email.strip()));
  }

  /** Se a función «¿Es un concello?» existe neste despregue. */
  public boolean enabled() {
    return email.isPresent();
  }

  private static void check(String value) {
    if (!SupportContact.isAddress(value)) {
      throw new IllegalArgumentException(
          "FURAR_JOIN_REQUEST_EMAIL: '" + value + "' is not an e-mail address");
    }
  }
}
