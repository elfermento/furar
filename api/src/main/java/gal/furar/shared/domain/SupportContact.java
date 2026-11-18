// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Contacto de soporte do despregue. Sae da configuración ({@code FURAR_SUPPORT_EMAIL}) e a web
 * móstrao no pé como ligazón {@code mailto:}. É opcional: baleiro, a web non mostra nada. Un valor
 * que non é un correo impide arrincar.
 *
 * @param email correo de soporte, tal como se escribiu e sen espazos ao redor; baleiro se non hai
 */
public record SupportContact(Optional<String> email) {

  /** Lonxitude máxima dun correo (RFC 5321). */
  public static final int MAX_LENGTH = 254;

  /**
   * Só o que vale tal cal nunha ligazón {@code mailto:}: letras ASCII, cifras e {@code . _ + -}
   * antes do {@code @}, e un dominio con polo menos un punto.
   */
  private static final Pattern EMAIL =
      Pattern.compile(
          "[A-Za-z0-9._+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");

  public SupportContact {
    Objects.requireNonNull(email, "email");
    email.ifPresent(SupportContact::check);
  }

  /**
   * Desde o valor de contorno.
   *
   * @param email o correo; {@code null}, baleiro ou só espazos = sen correo de soporte
   */
  public static SupportContact parse(String email) {
    if (email == null || email.isBlank()) {
      return new SupportContact(Optional.empty());
    }
    return new SupportContact(Optional.of(email.strip()));
  }

  /**
   * Se o valor é un único correo que vale tal cal nunha ligazón {@code mailto:} (tamén o usa o
   * destinatario das solicitudes de alta, {@link JoinRequestContact}).
   */
  public static boolean isAddress(String value) {
    return value.length() <= MAX_LENGTH && EMAIL.matcher(value).matches();
  }

  private static void check(String value) {
    if (!isAddress(value)) {
      throw new IllegalArgumentException(
          "FURAR_SUPPORT_EMAIL: '" + value + "' is not an e-mail address");
    }
  }
}
